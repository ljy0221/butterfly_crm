package com.b101.butterfly.reporting.audit.dto;

import com.b101.butterfly.reporting.audit.entity.AuditLog;
import com.b101.butterfly.reporting.audit.port.MemberLookupView;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 감사 로그 상세 응답 (API #21).
 *
 * <p>{@code memberName}, {@code memberEmail}은 {@link MemberLookupView}를 통해 채워진다.
 * lookup 결과가 없으면(예: 탈퇴 멤버) null 로 폴백한다.
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

    public static AuditLogDetailResponse from(AuditLog log, MemberLookupView member) {
        return new AuditLogDetailResponse(
                log.getId(),
                log.getProjectId(),
                log.getMemberId(),
                member != null ? member.name() : null,
                member != null ? member.email() : null,
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
