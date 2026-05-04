package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.service.preview.ConditionTreeHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Segment rebuild 메시지를 받아 segment_members를 빌드하는 worker. butterfly-worker에서만 활성화.
 *
 * <p><b>PR2 범위 (현재)</b> — 인프라 검증용 minimal:
 * <ul>
 *   <li>{@code markBuildingIfPossible} CAS로 빌드 점유 (다른 worker 동시 점유 거절)
 *   <li>{@code conditionTreeHash} 비교 — 빌드 시작 시점 tree와 다르면 reject
 *   <li>placeholder로 {@code markReady(0, ...)} 호출 (실제 매칭 user 조회는 PR3)
 * </ul>
 *
 * <p><b>PR3 추가 예정</b>:
 * <ul>
 *   <li>EVENT-only / TAG·USER·DEVICE-only 분기 (mixed는 reject — PreviewService와 동일)
 *   <li>{@code EventAggReader} / {@code UserDirectoryReader} 호출로 매칭 user_id Stream 받음
 *   <li>chunk 처리 (1000건 단위 INSERT) + chunk 사이 heartbeat 갱신
 *   <li>실제 estimated_count로 {@code completeBuildIfOwner} 갱신
 *   <li>실패 시 audit {@code SEGMENT_BUILD_FAILED} 발행
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

    private final SegmentRepository segmentRepository;
    private final ConditionTreeHasher conditionTreeHasher;

    @KafkaListener(
            topics = SegmentRebuildMessage.TOPIC,
            groupId = GROUP_ID,
            containerFactory = "segmentRebuildKafkaListenerContainerFactory")
    @Transactional
    public void consume(SegmentRebuildMessage message) {
        Long segmentId = message.segmentId();
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleThreshold = now.minus(STALE_TIMEOUT);

        // 1. 빌드 점유 시도 (CAS)
        int claimed =
                segmentRepository.markBuildingIfPossible(segmentId, token, now, staleThreshold);
        if (claimed == 0) {
            log.info(
                    "Segment {} 점유 실패 — 다른 worker가 처리 중 (heartbeat 신선). 메시지 ack 후 종료.",
                    segmentId);
            return;
        }
        log.info("Segment {} 점유 성공 (token={})", segmentId, token);

        // 2. conditionTree snapshot 검증 (Codex 안전장치 c)
        Optional<Segment> opt = segmentRepository.findById(segmentId);
        if (opt.isEmpty()) {
            // 빌드 중 segment soft-delete됨 (Codex 안전장치 b)
            log.warn(
                    "Segment {} 점유 후 조회 실패 — 빌드 중 삭제된 것으로 추정. failBuild로 마무리.",
                    segmentId);
            segmentRepository.failBuildIfOwner(segmentId, token);
            return;
        }
        Segment segment = opt.get();

        String currentHash =
                conditionTreeHasher.hash(message.projectId(), segment.getConditionTree());
        if (!currentHash.equals(message.conditionTreeHash())) {
            log.info(
                    "Segment {} conditionTree 변경 감지 — 새 rebuild 요청이 큐에 있을 것이므로 본 메시지 skip.",
                    segmentId);
            segmentRepository.failBuildIfOwner(segmentId, token);
            return;
        }

        // 3. (PR3) 실제 매칭 user 조회 + segment_members INSERT + chunk heartbeat
        // 현재 PR2는 인프라 검증만 — placeholder로 estimatedCount=0 마킹.
        int estimatedCount = 0;
        LocalDateTime builtAt = LocalDateTime.now();
        int updated =
                segmentRepository.completeBuildIfOwner(segmentId, token, estimatedCount, builtAt);
        if (updated == 0) {
            log.warn(
                    "Segment {} completeBuild 실패 — 다른 worker로 인수됐거나 종료됨 (token={}).",
                    segmentId,
                    token);
            return;
        }
        log.info("Segment {} 빌드 완료 placeholder (실제 멤버 INSERT는 PR3).", segmentId);
    }
}
