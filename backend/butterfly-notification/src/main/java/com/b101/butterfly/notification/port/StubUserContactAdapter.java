package com.b101.butterfly.notification.port;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnMissingBean(value = UserContactPort.class, ignored = StubUserContactAdapter.class)
public class StubUserContactAdapter implements UserContactPort {

    @Override
    public List<UserContactInfo> getContactsByUserIds(Long projectId, List<Long> userIds,
                                                      DeliveryCommand.Channel channel) {
        return userIds.stream()
                .map(userId -> new UserContactInfo(userId, null, null, null, null, null, null))
                .toList();
    }

    @Override
    public List<UserContactInfo> getAllContacts(Long projectId, DeliveryCommand.Channel channel) {
        return List.of();
    }
}
