package com.b101.butterfly.reporting.audit.dto;

import com.b101.butterfly.reporting.audit.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 감사 로그 상세 응답 (API #21).
 *
 * <p>{@code memberName}, {@code memberEmail}은 {@code MemberLookupPort} 도입 전까지는 null로 둔다.
 * (이유는 {@link AuditLogListItem} 참조)
 */
public record AuditLogDetailResponse(
        Long auditId,
        Long projectId,
        Long memberId,
        String memberName,
        String memberEmail,
        String action,
        String targetType,
        Long targetId,
        Map<String, Object> detail,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt
) {

    public static AuditLogDetailResponse from(AuditLog log) {
        return new AuditLogDetailResponse(
                log.getId(),
                log.getProjectId(),
                log.getMemberId(),
                null,
                null,
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetail(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedAt()
        );
    }
}
