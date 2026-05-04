package com.b101.butterfly.reporting.reconciliation.repository;

import com.b101.butterfly.reporting.reconciliation.entity.SendReconciliation;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SendReconciliation} 동적 검색용 Specification (API #24, #25).
 *
 * <p>모든 필터는 nullable이며 null은 무시된다. {@code consistent} 필터로 불일치 건만 조회
 * 가능 (API #25 의 의미).
 *
 * <p>인덱스 활용 (V2_reporting_baseline 정의): `idx_send_reconciliations_inconsistent`,
 * `idx_send_reconciliations_campaign`, `idx_send_reconciliations_execution`,
 * `idx_send_reconciliations_project_time`.
 */
public final class SendReconciliationSpecifications {

    private SendReconciliationSpecifications() {
    }

    public static Specification<SendReconciliation> withFilters(
            Long projectId,
            Long campaignId,
            Long executionId,
            Boolean consistent,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (campaignId != null) {
                predicates.add(cb.equal(root.get("campaignId"), campaignId));
            }
            if (executionId != null) {
                predicates.add(cb.equal(root.get("campaignExecutionId"), executionId));
            }
            if (consistent != null) {
                predicates.add(cb.equal(root.get("consistent"), consistent));
            }
            if (startDate != null) {
                LocalDateTime start = startDate.atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("checkedAt"), start));
            }
            if (endDate != null) {
                LocalDateTime end = endDate.atTime(LocalTime.MAX);
                predicates.add(cb.lessThanOrEqualTo(root.get("checkedAt"), end));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
