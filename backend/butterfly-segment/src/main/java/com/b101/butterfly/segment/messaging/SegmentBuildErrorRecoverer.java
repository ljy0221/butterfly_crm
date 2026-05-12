package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.stereotype.Component;

/**
 * Kafka {@code DefaultErrorHandler} 최종 실패 콜백 — segments 강제 FAILED 강등.
 *
 * <p>{@link SegmentBuildJob#consume} 처리 중 RuntimeException 이 발생하면
 * {@code DefaultErrorHandler} 가 backoff 정책에 따라 retry 한다. retry 가 모두 소진되면
 * 본 recoverer 가 호출되어 segments status 를 {@code FAILED} 로 강제 강등한다.
 *
 * <p><b>왜 강제 강등이 필요한가</b>:
 * <ul>
 *   <li>최종 실패한 메시지는 worker 가 {@code markBuildingIfPossible} 단계에서 깨졌을 수 있음
 *       (예: {@code TransactionRequiredException}) — 이 경우 token 자체가 commit 되지 않아
 *       {@code failBuildIfOwner} 의 token 매칭으로는 회수 불가
 *   <li>{@code SegmentBuildStaleCron} 은 BUILDING 만 잡으므로 PENDING 잔재는 자동 정리되지 않음
 *   <li>프론트는 PENDING 동안 재계산 버튼을 비활성화하므로 운영자가 빠져나갈 수 없는 deadlock
 * </ul>
 *
 * <p>본 recoverer 는 retry 가 다 소진된 시점에만 호출되므로 정상 worker 가 점유 중인
 * BUILDING row 와 충돌하지 않는다. retry 대상이 아닌 메시지는 도달하지 않음.
 */
@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class SegmentBuildErrorRecoverer implements ConsumerRecordRecoverer {

    private final SegmentRepository segmentRepository;

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception ex) {
        Object value = record.value();
        if (!(value instanceof SegmentRebuildMessage msg)) {
            log.error(
                    "최종 실패 메시지의 value 타입이 SegmentRebuildMessage 가 아님 — recovery 스킵: value={}, error={}",
                    value,
                    ex.toString());
            return;
        }

        Long segmentId = msg.segmentId();
        try {
            int updated = segmentRepository.forceFailById(segmentId);
            log.error(
                    "Segment {} 빌드 최종 실패 → FAILED 강제 강등 (updated={}). error={}",
                    segmentId,
                    updated,
                    ex.toString(),
                    ex);
        } catch (Exception recoveryEx) {
            // recovery 자체가 실패해도 listener 가 추가 retry 하지 않도록 swallow.
            // 운영 모니터링은 본 로그로 감지.
            log.error(
                    "Segment {} forceFailById 실패 — recovery 도중 예외. 운영 수동 개입 필요.",
                    segmentId,
                    recoveryEx);
        }
    }
}
