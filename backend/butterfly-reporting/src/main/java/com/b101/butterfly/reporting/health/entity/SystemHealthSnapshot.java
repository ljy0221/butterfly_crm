package com.b101.butterfly.reporting.health.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 시스템 건강 상태 시계열 스냅샷 (V2_reporting_baseline 의 system_health_snapshots 매핑).
 *
 * <p>운영 메트릭(kafka_lag, worker_tps, reconciliation_inconsistent_count 등)을 시계열로
 * 적재한다. 각 메트릭은 자체 정책으로 status 를 OK / WARN / CRITICAL 로 분류하므로 본 엔티티는
 * 측정값 + 임계치 + 상태만 보존한다.
 *
 * <p>정합성 검증 메트릭 적재는 {@code ReconciliationScheduler}(S14P31B101-187 PR-2 + PR-3) 가
 * 매일 02:00 KST 배치 종료 후 호출한다.
 */
@Entity
@Table(name = "system_health_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemHealthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long id;

    @Column(name = "metric_name", nullable = false, length = 50)
    private String metricName;

    @Column(name = "metric_target", length = 100)
    private String metricTarget;

    @Column(name = "metric_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal metricValue;

    @Column(name = "threshold_warn", precision = 18, scale = 4)
    private BigDecimal thresholdWarn;

    @Column(name = "threshold_crit", precision = 18, scale = 4)
    private BigDecimal thresholdCrit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HealthStatus status;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Builder(builderMethodName = "create", access = AccessLevel.PUBLIC)
    private SystemHealthSnapshot(
            String metricName,
            String metricTarget,
            BigDecimal metricValue,
            BigDecimal thresholdWarn,
            BigDecimal thresholdCrit,
            HealthStatus status,
            LocalDateTime collectedAt
    ) {
        this.metricName = metricName;
        this.metricTarget = metricTarget;
        this.metricValue = metricValue;
        this.thresholdWarn = thresholdWarn;
        this.thresholdCrit = thresholdCrit;
        this.status = status;
        this.collectedAt = collectedAt;
    }
}
