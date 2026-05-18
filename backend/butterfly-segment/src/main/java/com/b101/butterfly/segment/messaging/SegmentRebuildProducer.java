package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutionException;

/**
 * Segment rebuild 요청을 Kafka 토픽으로 발행. butterfly-api 프로세스에서만 활성화.
 *
 * <p>event 모듈 {@code EventProducer} 패턴 미러 — broker ack까지 동기 대기 (publish 성공 = 수락 모델).
 *
 * <p><b>S14P31B101-269</b>: 발행 시점이 트랜잭션 내부 inline 호출에서
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 으로 변경됨. {@link SegmentRebuildAcceptedEvent}
 * 를 받으면 발행한다. 트랜잭션 rollback 시에는 listener 가 호출되지 않아 DB-큐 일관성이 보장된다.
 */
@Slf4j
@Component
@Profile("api")
@RequiredArgsConstructor
public class SegmentRebuildProducer {

    private final KafkaTemplate<String, SegmentRebuildMessage> segmentRebuildKafkaTemplate;
    private final SegmentRepository segmentRepository;

    /**
     * {@link SegmentRebuildAcceptedEvent} 를 받아 Kafka 토픽으로 발행.
     *
     * <p>AFTER_COMMIT phase 라 listener 실행 시점에는 segments 행이 {@code markPending()} 적용된
     * 일관 상태 (PENDING + token=NULL + heartbeat=NULL). worker 가 메시지를 받았을 때 이미 정상
     * 상태이므로 markBuildingIfPossible 흐름이 lost update 없이 작동한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRebuildAccepted(SegmentRebuildAcceptedEvent event) {
        SegmentRebuildMessage message =
                new SegmentRebuildMessage(
                        event.segmentId(),
                        event.projectId(),
                        event.memberId(),
                        event.requestedAt(),
                        event.conditionTreeHash());
        try {
            publishRebuild(message);
        } catch (RuntimeException e) {
            int updated = segmentRepository.forceFailById(event.segmentId());
            log.error(
                    "Segment rebuild publish 실패 → FAILED 강제 강등: segmentId={}, updated={}",
                    event.segmentId(),
                    updated,
                    e);
        }
    }

    /**
     * rebuild 메시지를 발행한다. partition key는 segmentId 문자열 — 같은 segment의 메시지는 같은
     * 파티션으로 직렬화됨 (worker 측 처리 순서 보장).
     *
     * <p>일반적으로 {@link #onRebuildAccepted} 에서 호출. 직접 호출은 테스트 또는 특수 흐름용.
     */
    public void publishRebuild(SegmentRebuildMessage message) {
        try {
            segmentRebuildKafkaTemplate
                    .send(SegmentRebuildMessage.TOPIC, message.segmentId().toString(), message)
                    .get();
            log.info(
                    "Segment rebuild 요청 publish: segmentId={}, requestedBy={}",
                    message.segmentId(),
                    message.requestedBy());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publish interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Kafka publish failed", e.getCause());
        }
    }
}
