package com.b101.butterfly.notification.port;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;

import java.util.List;

public interface UserContactPort {

    List<UserContactInfo> getContactsByUserIds(Long projectId, List<Long> userIds,
                                               DeliveryCommand.Channel channel);
}
