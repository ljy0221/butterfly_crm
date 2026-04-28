package com.b101.butterfly.auth.dto.response;

import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.entity.Member;

import java.time.LocalDateTime;

public record MemberInfoResponse(
        Long memberId,
        String email,
        String name,
        String phone,
        RoleType roleType,
        Long projectId,
        LocalDateTime createdAt
) {
    public static MemberInfoResponse from(Member member) {
        return new MemberInfoResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getPhone(),
                member.getRoleType(),
                member.getProjectId(),
                member.getCreatedAt()
        );
    }
}
