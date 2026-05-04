package com.b101.butterfly.reporting.reconciliation.entity;

import com.b101.butterfly.reporting.notification.entity.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발송 정합성 검증 결과 (S14P31B101-126 / 126-A).
 *
 * <p>한 회차(`campaign_execution_id`)와 채널 단위로 expected(세그먼트 스냅샷 대상자 수) vs
 * actual(`notification_logs` row 수)을 비교한 결과를 보존한다. v2 스키마는
 * `backend/butterfly-api/src/main/resources/db/migration/V2__reporting_baseline.sql` 의
 * `send_reconciliations` 테이블 정의를 따른다.
 *
 * <p>멱등 재실행 정책: 같은 회차×채널을 다시 검증해도 새 row를 추가한다 — 이력 보존이
 * 운영 진단(시간순으로 일치율 변화 추적)에 유리하다. 가장 최근 결과만 보고 싶으면
 * `MAX(checked_at)` 기준 조회.
 */
@Entity
@Table(name = "send_reconciliations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SendReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recon_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "campaign_execution_id")
    private Long campaignExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;

    @Column(name = "expected_count", nullable = false)
    private int expectedCount;

    @Column(name = "actual_count", nullable = false)
    private int actualCount;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "diff_count", nullable = false)
    private int diffCount;

    @Column(name = "is_consistent", nullable = false)
    private boolean consistent;

    @Column(length = 500)
    private String note;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(builderMethodName = "create", access = AccessLevel.PUBLIC)
    private SendReconciliation(
            Long projectId,
            Long campaignId,
            Long campaignExecutionId,
            Channel channel,
            int expectedCount,
            int actualCount,
            int sentCount,
            int failedCount,
            String note,
            LocalDateTime checkedAt
    ) {
        this.projectId = projectId;
        this.campaignId = campaignId;
        this.campaignExecutionId = campaignExecutionId;
        this.channel = channel;
        this.expectedCount = expectedCount;
        this.actualCount = actualCount;
        this.sentCount = sentCount;
        this.failedCount = failedCount;
        this.diffCount = expectedCount - actualCount;
        this.consistent = (this.diffCount == 0);
        this.note = note;
        this.checkedAt = checkedAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
