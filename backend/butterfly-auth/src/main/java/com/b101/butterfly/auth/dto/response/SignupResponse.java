package com.b101.butterfly.auth.dto.response;

import com.b101.butterfly.auth.entity.Member;

public record SignupResponse(
        Long memberId,
        String email,
        String name
) {
    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getEmail(), member.getName());
    }
}
