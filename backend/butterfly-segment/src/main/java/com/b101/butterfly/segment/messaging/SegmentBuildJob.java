package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.port.EventAggReader;
import com.b101.butterfly.segment.port.EventQueryContext;
import com.b101.butterfly.segment.port.SegmentVariableSnapshotWriter;
import com.b101.butterfly.segment.port.UserDirectoryReader;
import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.service.match.ActiveEventSegmentCache;
import com.b101.butterfly.segment.service.preview.ConditionTreeHasher;
import com.b101.butterfly.segment.service.preview.ConditionTreeToSqlConverter;
import com.b101.butterfly.segment.service.preview.EventConditionNotReadyException;
import com.b101.butterfly.segment.service.preview.SqlFragment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Segment rebuild 메시지를 받아 segment_members를 빌드하는 worker. butterfly-worker에서만 활성화.
 *
 * <p>흐름:
 * <ol>
 *   <li>{@code markBuildingIfPossible} CAS로 점유 — 다른 worker 신선 처리 중이면 ack 후 종료
 *   <li>segment 조회 (Codex 안전장치 b: 빌드 중 soft-delete 시 fail)
 *   <li>conditionTreeHash 비교 (Codex 안전장치 c: 빌드 중 tree 수정 시 fail)
 *   <li>tree 분기 — filter 없는 EVENT-only 는 EventAggReader, 그 외 트리는 SQL 변환
 *   <li>매칭 user_id 적재 — 모든 경로가 단일 native {@code INSERT ... SELECT} (S14P31B101-269)
 *   <li>{@code completeBuildIfOwner}로 estimated_count 갱신 + token/heartbeat 정리
 * </ol>
 *
 * <p><b>트랜잭션 경계 (S14P31B101-243 Phase 1):</b>
 * {@code consume()} 메서드에는 의도적으로 {@code @Transactional} 을 걸지 않는다.
 * 각 repository 호출 (CAS UPDATE / findById / deleteBySegmentId / materialize / complete) 은
 * 자체적으로 짧은 트랜잭션을 시작·커밋한다. 이렇게 해야 long-running SELECT 가 진행되는 동안
 * segments 행 X-lock 과 connection 을 동시에 점유하지 않으므로 OLTP 락 카스케이드를 방지한다. {@code condition_tree} 는 {@code @Convert} 즉시 deserialize 이므로 detached 후에도 안전.
 *
 * <p>장애 복구: 메시지 처리 도중 worker 가 죽으면 {@code build_token} 잔재 + heartbeat 정지 →
 * {@code SegmentBuildStaleCron} 이 staleThreshold 후 FAILED 로 강등 → 다음 rebuild 가 정상 진입.
 *
 * <p><b>v1 한계</b> (PreviewService 동기화):
 * <ul>
 *   <li>filter 없는 EVENT 단독만 EventAggReader 호출, mixed/filtered EVENT는 SQL로 처리
 *   <li>{@code expires_at}은 항상 null (cumulative 의미). Period 기반 만료 정책은 후속 task
 *   <li>partial segment_members 잔재 정리는 Phase 2 영역 (build_token 컬럼 + READY-only 조회)
 * </ul>
 */
@Slf4j
@Component
@Lazy(false)
@Profile("worker")
@RequiredArgsConstructor
public class SegmentBuildJob {

    private static final String GROUP_ID = "segment-build-worker";

    /** stale 인수 timeout — {@code SegmentRebuildService}와 동일해야 일관성 유지. */
    private static final Duration STALE_TIMEOUT = Duration.ofMinutes(30);

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SegmentRepository segmentRepository;
    private final SegmentMemberRepository segmentMemberRepository;
    private final ConditionTreeHasher conditionTreeHasher;
    private final ConditionTreeToSqlConverter sqlConverter;
    private final UserDirectoryReader userDirectoryReader;
    private final EventAggReader eventAggReader;
    private final SegmentVariableSnapshotWriter segmentVariableSnapshotWriter;
    private final ActiveEventSegmentCache activeEventSegmentCache;

    @KafkaListener(
            topics = SegmentRebuildMessage.TOPIC,
            groupId = GROUP_ID,
            containerFactory = "segmentRebuildKafkaListenerContainerFactory")
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

            // Phase 6-3: READY 전환으로 활성 segment 목록이 바뀌었으므로 캐시 무효화
            activeEventSegmentCache.invalidate(message.projectId());
        } catch (RuntimeException e) {
            log.error(
                    "Segment {} 빌드 실패 (token={}): {}", segmentId, token, e.getMessage(), e);
            segmentRepository.failBuildIfOwner(segmentId, token);
            throw e; // Kafka 측 retry 정책에 위임
        }
    }

    /**
     * 매칭 user_id를 조회해 segment_members에 적재. 기존 멤버는 일괄 삭제 후 재구성.
     *
     * <p>모든 경로가 단일 native {@code INSERT ... SELECT}로 처리되어 Java 측 chunk fetch / batch
     * INSERT 가 사라진다. stale timeout(30분)에 한참 못 미치는 단일 statement 이므로 chunk-사이
     * heartbeat 가 필요 없다.
     *
     * <ul>
     *   <li>S14P31B101-199 — filter 없는 EVENT 단독 트리: {@code event_daily_agg} 기반 직행.
     *   <li>S14P31B101-269 — 그 외 (filtered EVENT / mixed): {@code ConditionTreeToSqlConverter}
     *       가 만든 SELECT 를 {@code UserDirectoryReader.materializeUserMatchesIntoSegment}
     *       로 한 statement 적재.
     * </ul>
     */
    private int buildMembers(Segment segment, Long projectId, String token) {
        Long segmentId = segment.getId();
        GroupNode tree = segment.getConditionTree();

        segmentMemberRepository.deleteBySegmentId(segmentId);

        if (isFilterlessEventOnlyTree(tree)) {
            EventNode eventNode = (EventNode) tree.conditions().get(0);
            EventQueryContext context =
                    new EventQueryContext(projectId, LocalDate.now(SEOUL), SEOUL);
            long inserted =
                    eventAggReader.materializeEventMatchesIntoSegment(
                            segmentId, eventNode, context);
            return Math.toIntExact(inserted);
        }

        SqlFragment fragment;
        try {
            EventQueryContext context =
                    new EventQueryContext(projectId, LocalDate.now(SEOUL), SEOUL);
            fragment = sqlConverter.convert(tree, context);
        } catch (EventConditionNotReadyException e) {
            throw new IllegalStateException("EVENT 조건 SQL 변환 준비 실패", e);
        }
        long inserted =
                userDirectoryReader.materializeUserMatchesIntoSegment(
                        segmentId, fragment.sql(), fragment.params());
        segmentVariableSnapshotWriter.refresh(projectId, segmentId, tree);
        return Math.toIntExact(inserted);
    }

    private static boolean isFilterlessEventOnlyTree(GroupNode tree) {
        return tree.conditions().size() == 1
                && tree.conditions().get(0) instanceof EventNode eventNode
                && (eventNode.filters() == null || eventNode.filters().isEmpty());
    }
}
