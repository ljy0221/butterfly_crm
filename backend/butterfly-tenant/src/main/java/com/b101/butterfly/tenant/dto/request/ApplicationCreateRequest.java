package com.b101.butterfly.tenant.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ApplicationCreateRequest(

        @NotBlank
        @Size(max = 100)
        String applicantName,

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Pattern(regexp = "^\\d{11}$", message = "전화번호는 11자리 숫자여야 합니다")
        String phone,

        @NotNull
        LocalDate birthDate,

        @NotBlank
        @Size(max = 255)
        String businessName,

        @NotBlank
        @Pattern(regexp = "^\\d{10}$", message = "사업자 번호는 숫자 10자리여야 합니다")
        String businessNo
) {}
