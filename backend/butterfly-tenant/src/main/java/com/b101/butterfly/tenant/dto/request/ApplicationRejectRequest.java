package com.b101.butterfly.tenant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicationRejectRequest(

        @NotBlank(message = "반려 사유를 입력해야 합니다")
        @Size(max = 500)
        String rejectReason
) {}
