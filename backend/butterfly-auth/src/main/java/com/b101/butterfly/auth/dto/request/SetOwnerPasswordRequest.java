package com.b101.butterfly.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetOwnerPasswordRequest(
        @NotBlank
        String token,

        @NotBlank @Size(min = 8, max = 20, message = "비밀번호는 8~20자입니다")
        String password
) {}
