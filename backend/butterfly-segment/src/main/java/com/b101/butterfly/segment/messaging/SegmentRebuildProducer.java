package com.b101.butterfly.segment.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * Segment rebuild 요청을 Kafka 토픽으로 발행. butterfly-api 프로세스에서만 활성화.
 *
 * <p>event 모듈 {@code EventProducer} 패턴 미러 — broker ack까지 동기 대기 후 응답
 * (publish 성공 = 수락 모델).
 */
@Slf4j
@Component
@Profile("api")
@RequiredArgsConstructor
public class SegmentRebuildProducer {

    private final KafkaTemplate<String, SegmentRebuildMessage> segmentRebuildKafkaTemplate;

    /**
     * rebuild 메시지를 발행한다. partition key는 segmentId 문자열 — 같은 segment의 메시지는 같은
     * 파티션으로 직렬화됨 (worker 측 처리 순서 보장).
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
