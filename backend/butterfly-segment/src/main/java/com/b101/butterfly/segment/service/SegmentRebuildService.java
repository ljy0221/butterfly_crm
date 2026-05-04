package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.segment.constant.AuditAction;
import com.b101.butterfly.segment.dto.response.RebuildResponse;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.messaging.SegmentRebuildMessage;
import com.b101.butterfly.segment.messaging.SegmentRebuildProducer;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.service.preview.ConditionTreeHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Segment rebuild API #44의 동기 부분 — 검증 → audit 발행 → Kafka publish.
 *
 * <p>실제 빌드 잡(매칭 user 조회, segment_members INSERT)은 worker의
 * {@code SegmentBuildJob}이 비동기로 처리한다. Codex 합의:
 * <ul>
 *   <li>본 서비스는 BUILDING 신선도 검증 후 메시지 발행만 (CAS 점유는 worker가)
 *   <li>conditionTreeHash 계산해 메시지에 포함 — worker가 처리 시점에 비교해 race condition 방지
 *   <li>audit는 메시지 발행 직전이 아니라 직후 — Kafka 실패 시 audit도 발행 X
 *   <li>{@code @Profile("api")} — worker에서는 빈 등록되지 않음 (Producer 의존)
 * </ul>
 *
 * <p>BUILDING 신선도 판정 timeout은 worker의 stale 인수 timeout과 동일해야 일관성 유지
 * (현재 30분 하드코딩, PR3에서 application.yml 외부화).
 */
@Slf4j
@Service
@Profile("api")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SegmentRebuildService {

    /** BUILDING 신선도 timeout — heartbeat 이 시간 이내면 다른 worker 점유 거절. */
    private static final Duration BUILD_FRESHNESS_TIMEOUT = Duration.ofMinutes(30);

    private final SegmentRepository segmentRepository;
    private final ConditionTreeHasher conditionTreeHasher;
    private final SegmentRebuildProducer segmentRebuildProducer;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * rebuild 요청 처리.
     *
     * <p>흐름:
     * <ol>
     *   <li>segment 조회 (soft-deleted 자동 제외)
     *   <li>BUILDING 신선도 검증 — 신선하면 {@code SEGMENT_409_BUILD_IN_PROGRESS}, stale은 허용
     *   <li>conditionTreeHash 계산
     *   <li>Kafka 메시지 발행 (broker ack까지 동기 대기)
     *   <li>audit {@code SEGMENT_REBUILD_REQUESTED} 발행
     *   <li>응답 반환 (buildStatus는 현재 값 그대로 — worker가 markBuildingIfPossible CAS로 점유)
     * </ol>
     */
    public RebuildResponse requestRebuild(Long projectId, Long segmentId, Long memberId) {
        Segment segment =
                segmentRepository
                        .findById(segmentId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                SegmentErrorCode.SEGMENT_404_NOT_FOUND));

        if (isFreshBuilding(segment)) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_409_BUILD_IN_PROGRESS);
        }

        String conditionTreeHash =
                conditionTreeHasher.hash(projectId, segment.getConditionTree());
        Instant requestedAt = Instant.now();

        segmentRebuildProducer.publishRebuild(
                new SegmentRebuildMessage(
                        segmentId, projectId, memberId, requestedAt, conditionTreeHash));

        eventPublisher.publishEvent(
                AuditRecordedEvent.of(
                        projectId,
                        memberId,
                        AuditAction.SEGMENT_REBUILD_REQUESTED,
                        "Segment",
                        segmentId,
                        null,
                        null,
                        Map.of("conditionTreeHash", conditionTreeHash)));

        log.info(
                "Segment rebuild 요청 수락: projectId={}, segmentId={}, memberId={}",
                projectId,
                segmentId,
                memberId);

        // 응답: buildStatus는 현재 값 — worker가 메시지 받으면 BUILDING으로 전환됨.
        // 클라이언트는 이후 segment 상세 조회로 진행 상황 추적.
        return new RebuildResponse(
                segmentId, segment.getBuildStatus(), LocalDateTime.now());
    }

    /**
     * BUILDING 신선도 판정 — {@code BUILDING}이면서 heartbeat가 timeout 이내면 신선(거절 대상).
     * stale 한 BUILDING은 worker가 인수해 갈 것이므로 새 rebuild 허용.
     */
    private boolean isFreshBuilding(Segment segment) {
        if (segment.getBuildStatus() != BuildStatus.BUILDING) {
            return false;
        }
        LocalDateTime heartbeat = segment.getBuildHeartbeatAt();
        if (heartbeat == null) {
            return false; // BUILDING이지만 heartbeat 없음 — 잔재로 판단
        }
        LocalDateTime threshold = LocalDateTime.now().minus(BUILD_FRESHNESS_TIMEOUT);
        return heartbeat.isAfter(threshold);
    }
}
