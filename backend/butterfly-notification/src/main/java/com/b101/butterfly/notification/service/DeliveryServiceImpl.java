package com.b101.butterfly.notification.service;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.response.DeliveryResult;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    @Override
    public void deliver(DeliveryCommand command) {
        if (command.targets() == null || command.targets().isEmpty()) {
            throw new BusinessException(DeliveryErrorCode.EMPTY_TARGETS);
        }

        ChannelAdapter adapter = channelAdapterRegistry.getAdapter(command.channel());

        // Phase 1: 발송 — 트랜잭션 밖에서 HTTP 호출
        List<DeliveryResultPersister.TargetOutcome> outcomes = new ArrayList<>();
        for (DeliveryCommand.DeliveryTarget target : command.targets()) {
            DeliveryResult result = adapter.send(command, target);
            outcomes.add(new DeliveryResultPersister.TargetOutcome(target, result));
        }

        // Phase 2: 결과 저장 — 별도 빈의 @Transactional 메서드 호출
        deliveryResultPersister.persist(command, outcomes);
    }
}
