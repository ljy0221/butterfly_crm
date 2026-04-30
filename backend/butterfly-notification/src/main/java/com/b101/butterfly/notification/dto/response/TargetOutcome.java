package com.b101.butterfly.notification.dto.response;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;

public record TargetOutcome(
        DeliveryCommand.DeliveryTarget target,
        DeliveryResult result
) {}
