package com.b101.butterfly.reporting.audit.repository;

import com.b101.butterfly.reporting.audit.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AuditLog} 동적 검색용 {@link Specification} 모음.
 *
 * <p>API #20(감사 로그 목록)은 5종 선택적 필터(projectId, memberId, action, targetType, 기간)를
 * 지원한다. {@code null} 필터는 무시되며, 모든 필터는 AND로 결합된다.
 *
 * <p>인덱스 활용(ADR-0008 §"인덱스 (v2 — 5종)"): {@code idx_audit_logs_project_action},
 * {@code idx_audit_logs_actor}, {@code idx_audit_logs_action_time} 등이 필터·정렬을 커버한다.
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> withFilters(
            Long projectId,
            Long memberId,
            String action,
            String targetType,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (memberId != null) {
                predicates.add(cb.equal(root.get("memberId"), memberId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (startDate != null) {
                LocalDateTime start = startDate.atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (endDate != null) {
                LocalDateTime end = endDate.atTime(LocalTime.MAX);
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
