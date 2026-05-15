package com.b101.butterfly.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentConfirmRequest(
        @NotBlank @Size(max = 64) String orderId,
        @NotBlank @Size(max = 200) String paymentKey,
        @NotNull @Positive Long amount
) {
}
