package com.b101.butterfly.reporting.audit.dto;

import com.b101.butterfly.reporting.audit.entity.AuditLog;

import java.time.LocalDateTime;

/**
 * 감사 로그 목록 응답 항목 (API #20).
 *
 * <p>명세 호환을 위해 {@code memberName}을 포함하지만, 현재 reporting 모듈에서 auth.entity.Member에
 * 직접 의존할 수 없어 일단 null로 채운다. {@code MemberLookupPort} 도입 후 별도 PR에서 채워 넣는다
 * (현재 path는 P4-A로 계획됨, 역할 1 담당).
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

    public static AuditLogListItem from(AuditLog log) {
        return new AuditLogListItem(
                log.getId(),
                log.getProjectId(),
                log.getMemberId(),
                null,
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedAt()
        );
    }
}
