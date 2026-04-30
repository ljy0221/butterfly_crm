package com.b101.butterfly.notification.service;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 모든 ChannelAdapter 빈을 자동 수집하여 채널별로 라우팅.
 */
@Slf4j
@Component
public class ChannelAdapterRegistry {

    private final Map<DeliveryCommand.Channel, ChannelAdapter> adapters;

    public ChannelAdapterRegistry(List<ChannelAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(
                        ChannelAdapter::supportedChannel,
                        Function.identity()
                ));
        log.info("ChannelAdapterRegistry initialized with {} adapter(s): {}",
                adapters.size(), adapters.keySet());
    }

    public ChannelAdapter getAdapter(DeliveryCommand.Channel channel) {
        ChannelAdapter adapter = adapters.get(channel);
        if (adapter == null) {
            throw new BusinessException(DeliveryErrorCode.UNSUPPORTED_CHANNEL);
        }
        return adapter;
    }
}
