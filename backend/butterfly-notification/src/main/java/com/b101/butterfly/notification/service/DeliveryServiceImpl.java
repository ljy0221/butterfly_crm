package com.b101.butterfly.notification.service;

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

    @Override
    public void deliver(DeliveryCommand command) {
        // 1. credential 결정: 있으면 그대로, 없으면 DB 조회
        ChannelCredential credential = command.credential() != null
                ? command.credential()
                : channelCredentialService.getCredential(command.projectId(), command.channel());

        // 2. targets 결정: 있으면 그대로, 없으면 userIds로 조회
        List<DeliveryTarget> targets = command.targets() != null && !command.targets().isEmpty()
                ? command.targets()
                : userContactPort.getContactsByUserIds(
                        command.projectId(), command.userIds(), command.channel())
                    .stream()
                    .map(c -> new DeliveryTarget(
                            c.userId(), c.deviceId(),
                            c.pushToken(), c.email(), c.phoneNumber()))
                    .toList();

        if (targets.isEmpty()) {
            throw new BusinessException(DeliveryErrorCode.EMPTY_TARGETS);
        }

        // 3. 내부용 command 재조립 (credential + targets 확정)
        DeliveryCommand resolved = new DeliveryCommand(
                command.projectId(), command.campaignId(), command.channel(),
                command.message(), command.userIds(), credential, targets);

        ChannelAdapter adapter = channelAdapterRegistry.getAdapter(command.channel());

        // Phase 1: 발송 — 트랜잭션 밖에서 HTTP 호출
        List<TargetOutcome> outcomes = adapter.sendBatch(resolved, targets);

        // Phase 2: 결과 저장 — 별도 빈의 @Transactional 메서드 호출
        deliveryResultPersister.persist(resolved, outcomes);
    }
}
