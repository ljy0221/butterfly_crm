package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.segment.port.EventAggReader;
import com.b101.butterfly.segment.port.EventQueryContext;
import com.b101.butterfly.segment.port.UserDirectoryReader;
import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.service.preview.ConditionTreeHasher;
import com.b101.butterfly.segment.service.preview.ConditionTreeToSqlConverter;
import com.b101.butterfly.segment.service.preview.EventConditionNotReadyException;
import com.b101.butterfly.segment.service.preview.SqlFragment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Segment rebuild 메시지를 받아 segment_members를 빌드하는 worker. butterfly-worker에서만 활성화.
 *
 * <p>흐름 (PR3 완성형):
 * <ol>
 *   <li>{@code markBuildingIfPossible} CAS로 점유 — 다른 worker 신선 처리 중이면 ack 후 종료
 *   <li>segment 조회 (Codex 안전장치 b: 빌드 중 soft-delete 시 fail)
 *   <li>conditionTreeHash 비교 (Codex 안전장치 c: 빌드 중 tree 수정 시 fail)
 *   <li>tree 분기 — EVENT-only / TAG·USER·DEVICE-only / mixed (mixed는 fail)
 *   <li>매칭 user_id 조회 — {@link EventAggReader} 또는 {@link UserDirectoryReader}
 *   <li>기존 segment_members 일괄 삭제 → chunk(1000건) 단위 INSERT + chunk 후 heartbeat 갱신
 *   <li>{@code completeBuildIfOwner}로 estimated_count 갱신 + token/heartbeat 정리
 * </ol>
 *
 * <p><b>v1 한계</b> (PreviewService 동기화):
 * <ul>
 *   <li>EVENT 단독만 EventAggReader 호출, mixed 트리는 거절 (ADR-0006 §3 + Codex 합의)
 *   <li>EVENT 노드 filters는 검증 단계에서 reject되므로 여기까지 안 도달
 *   <li>{@code expires_at}은 항상 null (cumulative 의미). Period 기반 만료 정책은 후속 task
 * </ul>
 */
@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class SegmentBuildJob {

    private static final String GROUP_ID = "segment-build-worker";

    /** stale 인수 timeout — {@code SegmentRebuildService}와 동일해야 일관성 유지. */
    private static final Duration STALE_TIMEOUT = Duration.ofMinutes(30);

    /** chunk INSERT 크기 — 너무 크면 트랜잭션 길어지고 너무 작으면 RTT 비용 ↑. 1000건 기준. */
    private static final int CHUNK_SIZE = 1000;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SegmentRepository segmentRepository;
    private final SegmentMemberRepository segmentMemberRepository;
    private final ConditionTreeHasher conditionTreeHasher;
    private final ConditionTreeToSqlConverter sqlConverter;
    private final UserDirectoryReader userDirectoryReader;
    private final EventAggReader eventAggReader;

    @KafkaListener(
            topics = SegmentRebuildMessage.TOPIC,
            groupId = GROUP_ID,
            containerFactory = "segmentRebuildKafkaListenerContainerFactory")
    @Transactional
    public void consume(SegmentRebuildMessage message) {
        Long segmentId = message.segmentId();
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        // 1. 빌드 점유 시도 (CAS)
        int claimed =
                segmentRepository.markBuildingIfPossible(
                        segmentId, token, now, now.minus(STALE_TIMEOUT));
        if (claimed == 0) {
            log.info("Segment {} 점유 실패 — 다른 worker 신선 처리 중. ack 후 종료.", segmentId);
            return;
        }
        log.info("Segment {} 점유 성공 (token={})", segmentId, token);

        // 2. segment 조회 (빌드 중 삭제 감지)
        Optional<Segment> opt = segmentRepository.findById(segmentId);
        if (opt.isEmpty()) {
            log.warn("Segment {} 점유 후 조회 실패 — 빌드 중 삭제로 추정. failBuild.", segmentId);
            segmentRepository.failBuildIfOwner(segmentId, token);
            return;
        }
        Segment segment = opt.get();

        // 3. conditionTreeHash 비교 (빌드 중 tree 수정 감지)
        String currentHash =
                conditionTreeHasher.hash(message.projectId(), segment.getConditionTree());
        if (!currentHash.equals(message.conditionTreeHash())) {
            log.info(
                    "Segment {} conditionTree 변경 감지 — 새 rebuild가 큐에 있을 것이므로 본 메시지 skip.",
                    segmentId);
            segmentRepository.failBuildIfOwner(segmentId, token);
            return;
        }

        // 4. 실제 매칭 user 조회 + chunk INSERT
        try {
            int totalCount = buildMembers(segment, message.projectId(), token);
            int updated =
                    segmentRepository.completeBuildIfOwner(
                            segmentId, token, totalCount, LocalDateTime.now());
            if (updated == 0) {
                log.warn(
                        "Segment {} completeBuild 실패 — 다른 worker로 인수됐거나 종료됨. (token={})",
                        segmentId,
                        token);
                return;
            }
            log.info("Segment {} 빌드 완료. estimatedCount={}", segmentId, totalCount);
        } catch (RuntimeException e) {
            log.error(
                    "Segment {} 빌드 실패 (token={}): {}", segmentId, token, e.getMessage(), e);
            segmentRepository.failBuildIfOwner(segmentId, token);
            throw e; // Kafka 측 retry 정책에 위임
        }
    }

    /**
     * 매칭 user_id를 조회해 chunk INSERT. 기존 멤버는 일괄 삭제 후 재구성.
     * chunk 사이마다 heartbeat 갱신해 cron 의 stale 판정 회피.
     */
    private int buildMembers(Segment segment, Long projectId, String token) {
        Long segmentId = segment.getId();
        GroupNode tree = segment.getConditionTree();

        segmentMemberRepository.deleteBySegmentId(segmentId);

        if (isEventOnlyTree(tree)) {
            EventNode eventNode = (EventNode) tree.conditions().get(0);
            EventQueryContext context =
                    new EventQueryContext(projectId, LocalDate.now(SEOUL), SEOUL);
            try (Stream<Long> stream = eventAggReader.queryMatchingUserIds(eventNode, context)) {
                return insertInChunks(segmentId, token, stream);
            }
        }

        if (containsEventNode(tree)) {
            // mixed 트리는 v1 미지원 — Validator/Service 단계에서 막혀야 정상이지만 방어.
            log.warn(
                    "Segment {} mixed 트리 (EVENT + 기타) 감지 — v1 미지원. failBuild.", segmentId);
            throw new IllegalStateException(
                    "Segment " + segmentId + " has mixed EVENT+other tree (v1 unsupported)");
        }

        SqlFragment fragment;
        try {
            fragment = sqlConverter.convert(tree, projectId);
        } catch (EventConditionNotReadyException e) {
            // 방어적 — TAG/USER/DEVICE 분기는 EVENT 미포함이므로 도달 안 함
            throw new IllegalStateException("Unexpected EVENT in non-EVENT branch", e);
        }
        List<Long> userIds = userDirectoryReader.queryUserIds(fragment.sql(), fragment.params());
        return insertInChunks(segmentId, token, userIds.stream());
    }

    private int insertInChunks(Long segmentId, String token, Stream<Long> userIdStream) {
        List<SegmentMember> buffer = new ArrayList<>(CHUNK_SIZE);
        int total = 0;
        for (Long userId : (Iterable<Long>) userIdStream::iterator) {
            buffer.add(SegmentMember.builder().segmentId(segmentId).userId(userId).build());
            if (buffer.size() >= CHUNK_SIZE) {
                segmentMemberRepository.saveAll(buffer);
                total += buffer.size();
                buffer.clear();
                segmentRepository.updateHeartbeat(segmentId, token, LocalDateTime.now());
            }
        }
        if (!buffer.isEmpty()) {
            segmentMemberRepository.saveAll(buffer);
            total += buffer.size();
        }
        return total;
    }

    private static boolean isEventOnlyTree(GroupNode tree) {
        return tree.conditions().size() == 1 && tree.conditions().get(0) instanceof EventNode;
    }

    private static boolean containsEventNode(ConditionNode node) {
        if (node instanceof EventNode) {
            return true;
        }
        if (node instanceof GroupNode g) {
            for (ConditionNode child : g.conditions()) {
                if (containsEventNode(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
