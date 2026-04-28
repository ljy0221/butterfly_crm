package com.b101.butterfly.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record WithdrawRequest(
        @NotBlank
        String password
) {}
