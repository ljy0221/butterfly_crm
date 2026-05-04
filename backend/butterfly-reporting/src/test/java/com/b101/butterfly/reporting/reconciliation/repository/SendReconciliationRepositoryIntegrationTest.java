package com.b101.butterfly.reporting.reconciliation.repository;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.reconciliation.entity.SendReconciliation;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S14P31B101-126 SendReconciliation 매핑 + Specifications 동작 슬라이스 테스트.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SendReconciliationRepositoryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private SendReconciliationRepository repository;

    @BeforeEach
    void seed() {
        // (project=123, campaign=1, execution=10, PUSH, consistent)
        repository.save(SendReconciliation.create()
                .projectId(123L).campaignId(1L).campaignExecutionId(10L)
                .channel(Channel.PUSH)
                .expectedCount(100).actualCount(100).sentCount(100).failedCount(0)
                .checkedAt(LocalDateTime.of(2026, 4, 28, 12, 0))
                .build());

        // (project=123, campaign=1, execution=11, PUSH, inconsistent)
        repository.save(SendReconciliation.create()
                .projectId(123L).campaignId(1L).campaignExecutionId(11L)
                .channel(Channel.PUSH)
                .expectedCount(200).actualCount(195).sentCount(190).failedCount(5)
                .note("5건 누락 감지")
                .checkedAt(LocalDateTime.of(2026, 4, 29, 12, 0))
                .build());

        // (project=124, 다른 프로젝트, inconsistent)
        repository.save(SendReconciliation.create()
                .projectId(124L).campaignId(2L).campaignExecutionId(20L)
                .channel(Channel.EMAIL)
                .expectedCount(50).actualCount(48).sentCount(46).failedCount(2)
                .note("2건 누락 감지")
                .checkedAt(LocalDateTime.of(2026, 4, 29, 13, 0))
                .build());
    }

    @Test
    @DisplayName("필터 없이 모든 row 조회")
    void noFilters() {
        Specification<SendReconciliation> spec = SendReconciliationSpecifications.withFilters(
                null, null, null, null, null, null);

        long count = repository.count(spec);

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("projectId 필터")
    void projectIdFilter() {
        Specification<SendReconciliation> spec = SendReconciliationSpecifications.withFilters(
                123L, null, null, null, null, null);

        assertThat(repository.count(spec)).isEqualTo(2);
    }

    @Test
    @DisplayName("consistent=false 필터 → 불일치만")
    void consistentFalseFilter() {
        Specification<SendReconciliation> spec = SendReconciliationSpecifications.withFilters(
                null, null, null, false, null, null);

        long count = repository.count(spec);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("executionId 필터로 단일 row 조회")
    void executionIdFilter() {
        Specification<SendReconciliation> spec = SendReconciliationSpecifications.withFilters(
                null, null, 10L, null, null, null);

        long count = repository.count(spec);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("기간 필터 — startDate, endDate (KST 자정 기준)")
    void dateRangeFilter() {
        Specification<SendReconciliation> spec = SendReconciliationSpecifications.withFilters(
                null, null, null, null,
                java.time.LocalDate.of(2026, 4, 29),
                java.time.LocalDate.of(2026, 4, 29));

        // checkedAt이 2026-04-29 인 두 건만 잡힘 (28일 row 제외)
        assertThat(repository.count(spec)).isEqualTo(2);
    }

    @Test
    @DisplayName("페이지네이션 + 최신순 (checkedAt DESC)")
    void paginationAndSort() {
        Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "checkedAt"));
        Specification<SendReconciliation> spec = SendReconciliationSpecifications.withFilters(
                null, null, null, null, null, null);

        var result = repository.findAll(spec, pageable);

        assertThat(result.getContent()).hasSize(2);
        // checkedAt이 가장 큰 것이 먼저 나와야 함 — 124 프로젝트(2026-04-29 13:00)가 첫번째
        assertThat(result.getContent().get(0).getProjectId()).isEqualTo(124L);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("create() 빌더가 diffCount, consistent를 자동 계산")
    void diffCountAndConsistentAreDerived() {
        SendReconciliation row = SendReconciliation.create()
                .projectId(123L).campaignId(1L).campaignExecutionId(99L)
                .channel(Channel.PUSH)
                .expectedCount(10).actualCount(7).sentCount(7).failedCount(0)
                .checkedAt(LocalDateTime.now())
                .build();

        assertThat(row.getDiffCount()).isEqualTo(3);
        assertThat(row.isConsistent()).isFalse();
    }
}
