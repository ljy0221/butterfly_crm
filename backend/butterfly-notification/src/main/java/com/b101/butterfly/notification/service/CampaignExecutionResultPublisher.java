package com.b101.butterfly.notification.service;

import com.b101.butterfly.campaign.lifecycle.port.CampaignExecutionResult;
import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.response.TargetOutcome;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 한 회차 발송이 끝난 직후 per-execution summary 를 {@code campaign-execution-result} 토픽으로 발행.
 *
 * <p>{@link DeliveryServiceImpl#deliver} 의 Phase 3 에 해당 — Phase 2(DB 적재) 이후, 외부 IO 라 트랜잭션 밖에서 수행.
 * 트랜잭션 안 publish 를 피하는 이유는 publish 시간 동안 DB 커넥션을 점유하지 않기 위해서
 * (Phase 1/Phase 2 분리와 같은 원칙).
 *
 * <p>SUCCESS/FAIL 판정은 consumer(worker) 측 단일 지점에서 한다. 본 publisher 는 정직하게
 * sent/failed 카운트만 집계해 보낸다 — 판정 logic 을 분산하지 않음.
 *
 * <p>{@code executionId == null} 인 command (레거시/테스트 경로) 는 publish 를 건너뛴다 —
 * 정상 캠페인 발송 흐름은 worker 가 항상 채워 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignExecutionResultPublisher {

    static final String TOPIC = "campaign-execution-result";

    private final KafkaTemplate<String, CampaignExecutionResult> campaignExecutionResultKafkaTemplate;

    public void publish(DeliveryCommand command, List<TargetOutcome> outcomes) {
        if (command.executionId() == null) {
            return;
        }

        int sent = (int) outcomes.stream().filter(o -> o.result().successful()).count();
        int failed = outcomes.size() - sent;

        CampaignExecutionResult payload = CampaignExecutionResult.of(
                command.executionId(),
                command.campaignId(),
                command.projectId(),
                sent,
                failed,
                Instant.now()
        );

        campaignExecutionResultKafkaTemplate.send(
                TOPIC,
                command.projectId().toString(),
                payload
        );

        log.info("CampaignExecutionResult 발행: executionId={}, campaignId={}, sent={}, failed={}",
                command.executionId(), command.campaignId(), sent, failed);
    }
}
