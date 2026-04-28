package com.b101.butterfly.auth.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SignupRequest(
        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8, max = 20, message = "비밀번호는 8~20자입니다")
        String password,

        @NotBlank @Size(max = 20)
        String name,

        @NotBlank @Pattern(regexp = "^\\d{11}$", message = "전화번호는 11자리 숫자여야 합니다")
        String phone,

        @NotNull
        LocalDate birthDate,

        @NotBlank
        String companyAuthNo,

        @AssertTrue(message = "이용약관에 동의해야 합니다")
        boolean agreedToTerms
) {}
