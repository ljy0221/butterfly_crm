package com.b101.butterfly.delivery.dto.response;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;

public record TargetOutcome(
        DeliveryCommand.DeliveryTarget target,
        DeliveryResult result
) {}
