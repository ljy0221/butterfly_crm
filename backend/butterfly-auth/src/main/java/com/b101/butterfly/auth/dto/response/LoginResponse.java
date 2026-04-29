package com.b101.butterfly.auth.dto.response;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        MemberInfoResponse member
) {}
