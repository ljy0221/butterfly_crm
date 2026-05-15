package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.segment.constant.AuditAction;
import com.b101.butterfly.segment.dto.response.CancelBuildResponse;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Segment 빌드 취소 API — BUILDING/PENDING 상태의 빌드를 즉시 FAILED 로 강등하고 cancel marker 를
 * token 에 박는다 (S14P31B101-269).
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link SegmentRepository#cancelBuildIfPossible} 단일 UPDATE 로 상태 전환 + marker 마킹
 *   <li>marker 가 박혀 있는 동안 worker 의 {@code markBuildingIfPossible} 점유 차단 — Kafka retry
 *       또는 새 메시지가 들어와도 INSERT-SELECT 시작 불가
 *   <li>사용자가 새 rebuild 요청 시 {@code Segment#markPending()} 이 token 을 NULL reset 해 marker
 *       자동 해제 → 정상 흐름 복귀
 * </ol>
 *
 * <p>본 서비스는 worker 의 다음 step 과 새 메시지 처리만 차단. 진행 중인 INSERT-SELECT 자체를
 * 끊으려면 DBA 권한으로 직접 KILL 이 필요 — 별도 운영 흐름.
 *
 * <p>{@code @Profile("api")} — worker 컨텍스트에는 빈 등록되지 않음 (controller 는 worker 에도
 * 등록되므로 {@code ObjectProvider} 로 lazy resolution).
 */
@Slf4j
@Service
@Profile("api")
@RequiredArgsConstructor
public class SegmentBuildCancelService {

    private static final String CANCEL_MARKER_PREFIX = "cancel-";

    private final SegmentRepository segmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CancelBuildResponse cancelBuild(Long projectId, Long segmentId, Long memberId) {
        // segment 존재 여부는 cancelBuildIfPossible 의 row 매칭으로 판정 — 별도 findById 안 함
        // (race 최소화: 단일 UPDATE statement).
        String marker = CANCEL_MARKER_PREFIX + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        int updated = segmentRepository.cancelBuildIfPossible(segmentId, marker, now);

        if (updated == 0) {
            // BUILDING/PENDING 아닌 상태 (READY/FAILED/이미 cancel 된 segment) — 취소 대상 없음.
            throw new BusinessException(SegmentErrorCode.SEGMENT_409_NO_CANCELLABLE_BUILD);
        }

        eventPublisher.publishEvent(
                AuditRecordedEvent.of(
                        projectId,
                        memberId,
                        AuditAction.SEGMENT_BUILD_CANCELED,
                        "Segment",
                        segmentId,
                        null,
                        null,
                        null));

        log.info(
                "Segment build 취소: projectId={}, segmentId={}, memberId={}, marker={}",
                projectId,
                segmentId,
                memberId,
                marker);

        return new CancelBuildResponse(segmentId, BuildStatus.FAILED, now);
    }
}
