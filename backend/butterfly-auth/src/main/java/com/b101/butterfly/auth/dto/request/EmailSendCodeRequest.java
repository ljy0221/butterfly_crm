package com.b101.butterfly.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailSendCodeRequest(
        @NotBlank @Email
        String email
) {}
