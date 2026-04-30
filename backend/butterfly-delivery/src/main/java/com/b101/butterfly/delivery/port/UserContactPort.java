package com.b101.butterfly.delivery.port;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;

import java.util.List;

public interface UserContactPort {

    List<UserContactInfo> getContactsByUserIds(Long projectId, List<Long> userIds,
                                               DeliveryCommand.Channel channel);
}
