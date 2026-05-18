package com.b101.butterfly.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentOrderCreateRequest(
        @NotNull @Positive Long amount
) {
}
