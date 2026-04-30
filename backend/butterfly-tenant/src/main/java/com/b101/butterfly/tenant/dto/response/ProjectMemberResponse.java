package com.b101.butterfly.tenant.dto.response;

import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.entity.Member;

import java.time.LocalDate;

public record ProjectMemberResponse(
        Long memberId,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        RoleType roleType
) {
    public static ProjectMemberResponse from(Member member) {
        return new ProjectMemberResponse(
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getPhone(),
                member.getBirthDate(),
                member.getRoleType()
        );
    }
}
