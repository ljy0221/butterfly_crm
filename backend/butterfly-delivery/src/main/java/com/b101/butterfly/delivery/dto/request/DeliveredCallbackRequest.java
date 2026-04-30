package com.b101.butterfly.delivery.dto.request;

import java.time.LocalDateTime;

public record DeliveredCallbackRequest(
        LocalDateTime deliveredAt
) {}
