package com.b101.butterfly.reporting.audit.dto;

import com.b101.butterfly.reporting.audit.entity.AuditLog;
import com.b101.butterfly.reporting.audit.port.MemberLookupView;

import java.time.LocalDateTime;

/**
 * 감사 로그 목록 응답 항목 (API #20).
 *
 * <p>{@code memberName}은 {@link MemberLookupView}를 통해 채워진다. lookup 결과가 없으면(예: 탈퇴 멤버)
 * null 로 폴백한다 — 프론트는 ID-only 표시로 그레이스풀 디그레이드.
 */
public record AuditLogListItem(
        Long auditId,
        Long projectId,
        Long memberId,
        String memberName,
        String action,
        String targetType,
        Long targetId,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt
) {

    public static AuditLogListItem from(AuditLog log, MemberLookupView member) {
        return new AuditLogListItem(
                log.getId(),
                log.getProjectId(),
                log.getMemberId(),
                member != null ? member.name() : null,
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedAt()
        );
    }
}
