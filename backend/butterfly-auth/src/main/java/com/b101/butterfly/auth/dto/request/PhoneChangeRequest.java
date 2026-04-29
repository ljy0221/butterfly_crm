package com.b101.butterfly.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneChangeRequest(
        @NotBlank @Pattern(regexp = "^\\d{11}$", message = "전화번호는 11자리 숫자여야 합니다")
        String phone
) {}
