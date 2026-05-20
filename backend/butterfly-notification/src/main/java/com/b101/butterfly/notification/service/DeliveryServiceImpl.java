package com.b101.butterfly.notification.service;

import com.b101.butterfly.event.port.UserTagLookupPort;
import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.ChannelCredential;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.DeliveryTarget;
import com.b101.butterfly.notification.dto.response.TargetOutcome;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.b101.butterfly.notification.port.UserContactPort;
import com.b101.butterfly.notification.service.credential.ChannelCredentialService;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 발송 오케스트레이션.
 * HTTP 호출(Phase 1)과 DB 저장(Phase 2)을 분리하여
 * 외부 API 호출 중 DB 커넥션을 점유하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    private final ChannelAdapterRegistry channelAdapterRegistry;
    private final DeliveryResultPersister deliveryResultPersister;
    private final ChannelCredentialService channelCredentialService;
    private final UserContactPort userContactPort;
    private final UserTagLookupPort userTagLookupPort;
    private final CampaignExecutionResultPublisher campaignExecutionResultPublisher;
    private final com.b101.butterfly.notification.messaging.NotificationSendResultPublisher notificationSendResultPublisher;

    @Override
    public void deliver(DeliveryCommand command) {
        log.info("발송 시작: executionId={}, campaignId={}, channel={}",
                command.executionId(), command.campaignId(), command.channel());

        // 1. credential 결정: 있으면 그대로, 없으면 DB 조회
        ChannelCredential credential = command.credential() != null
                ? command.credential()
                : channelCredentialService.getCredential(command.projectId(), command.channel());

        // 2. targets 결정: 있으면 그대로, 없으면 userIds 로 조회.
        //    userIds 경로일 때도 user_tags 를 함께 조회해 variables 를 채운다 — 누락하면
        //    뒤이은 personalize() 가 {{name}} 같은 placeholder 를 빈 문자열로 치환해버려
        //    개인화가 사라진다. (worker fan-out 의 CampaignSendInstructionProcessor 와 동일한 조립 규칙)
        final List<DeliveryTarget> targets;
        if (command.targets() != null && !command.targets().isEmpty()) {
            targets = command.targets();
        } else {
            List<Long> userIds = command.userIds();
            Map<Long, Map<String, String>> userTags = userTagLookupPort.getTagsByUserIds(
                    command.projectId(), userIds);
            targets = userContactPort.getContactsByUserIds(
                            command.projectId(), userIds, command.channel())
                    .stream()
                    .map(c -> new DeliveryTarget(
                            c.userId(), c.deviceId(),
                            c.pushToken(), c.email(), c.phoneNumber(),
                            userTags.getOrDefault(c.userId(), Map.of())))
                    .toList();
        }

        if (targets.isEmpty()) {
            log.warn("발송 대상 없음: executionId={}, campaignId={}", command.executionId(), command.campaignId());
            throw new BusinessException(DeliveryErrorCode.EMPTY_TARGETS);
        }

        log.info("발송 대상 확정: executionId={}, targetCount={}", command.executionId(), targets.size());

        // 3. 내부용 command 재조립 (credential + targets 확정)
        DeliveryCommand resolved = new DeliveryCommand(
                command.projectId(), command.campaignId(), command.executionId(),
                command.channel(), command.message(), command.userIds(), credential, targets);

        ChannelAdapter adapter = channelAdapterRegistry.getAdapter(command.channel());

        // Phase 1: 발송 — 트랜잭션 밖에서 HTTP 호출
        log.info("발송 Phase1 시작(채널 전송): executionId={}, targetCount={}", command.executionId(), targets.size());
        List<TargetOutcome> outcomes = adapter.sendBatch(resolved, targets);
        long sentCount = outcomes.stream().filter(o -> o.result().successful()).count();
        long failedCount = outcomes.size() - sentCount;
        log.info("발송 Phase1 완료: executionId={}, sent={}, failed={}", command.executionId(), sentCount, failedCount);

        // Phase 2: 결과 저장 — 별도 빈의 @Transactional 메서드 호출
        log.info("발송 Phase2 시작(결과 저장): executionId={}", command.executionId());
        deliveryResultPersister.persist(resolved, outcomes);

        // Phase 3: per-execution summary 발행 (campaign-execution-result 토픽)
        //   — worker 측 consumer 가 받아 CampaignExecution.status SEND → SUCCESS|FAIL 전이.
        //   외부 IO 라 트랜잭션 밖에서 수행 (Phase 1/2 분리와 같은 원칙).
        log.info("발송 Phase3 시작(실행 결과 발행): executionId={}", command.executionId());
        campaignExecutionResultPublisher.publish(resolved, outcomes);

        // Phase 4: 건별 발송 결과 발행 (notification-send-result 토픽)
        //   — reporting 모듈 NotificationSendResultConsumer 가 분석용 notification_logs 에 적재.
        log.info("발송 Phase4 시작(분석 로그 발행): executionId={}", command.executionId());
        notificationSendResultPublisher.publishAll(resolved, outcomes);
    }
}
