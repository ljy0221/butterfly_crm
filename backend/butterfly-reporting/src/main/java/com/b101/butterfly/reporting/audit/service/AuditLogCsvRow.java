package com.b101.butterfly.reporting.audit.service;

import com.b101.butterfly.reporting.audit.entity.AuditLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * AuditLog 엔티티를 CSV row로 직렬화 (S14P31B101-35 / 91).
 *
 * <p>컬럼 순서는 명세서 #20/#21 응답 필드와 동일하게 유지:
 * {@code auditId, projectId, memberId, action, targetType, targetId,
 *  ipAddress, userAgent, createdAt, detail}
 *
 * <p>{@code detail}은 JSON 문자열로 직렬화하며, CSV는 RFC 4180 이스케이프로 안전 처리한다.
 */
final class AuditLogCsvRow {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    static final List<String> HEADERS = List.of(
            "auditId", "projectId", "memberId",
            "action", "targetType", "targetId",
            "ipAddress", "userAgent",
            "createdAt", "detail"
    );

    private AuditLogCsvRow() {
    }

    static List<String> toRow(AuditLog log, ObjectMapper objectMapper) {
        return Arrays.asList(
                str(log.getId()),
                str(log.getProjectId()),
                str(log.getMemberId()),
                log.getAction(),
                log.getTargetType(),
                str(log.getTargetId()),
                log.getIpAddress(),
                log.getUserAgent(),
                formatTs(log.getCreatedAt()),
                serializeDetail(log.getDetail(), objectMapper)
        );
    }

    private static String str(Long value) {
        return value == null ? "" : value.toString();
    }

    private static String formatTs(LocalDateTime ts) {
        return ts == null ? "" : ts.format(TS);
    }

    private static String serializeDetail(Object detail, ObjectMapper objectMapper) {
        if (detail == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            // 직렬화 실패는 데이터 이슈가 아니라 코드 이슈 — Map 인스턴스에서 발생할 일은 없음.
            // 안전 fallback: 빈 문자열 (CSV 전체를 멈추기보다 한 cell만 비움).
            return "";
        }
    }
}
