package com.b101.butterfly.reporting.reconciliation.service;

import com.b101.butterfly.reporting.health.entity.HealthStatus;
import com.b101.butterfly.reporting.health.entity.SystemHealthSnapshot;
import com.b101.butterfly.reporting.health.repository.SystemHealthSnapshotRepository;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionInfo;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 정합성 검증 자동 배치 (S14P31B101-187 / 126-B PR-2).
 *
 * <p>매일 새벽 2시(KST) 직전 24시간 동안 실행된 회차들을 일괄 검증해
 * {@link com.b101.butterfly.reporting.reconciliation.entity.SendReconciliation} 행을 적재한다.
 * daily 롤업 ({@code DailyRollupService} cron 0 0 1 * * *) 가 끝난 직후 실행되어 같은
 * 데이터 윈도우의 발송 원장이 안정된 상태에서 검증한다.
 *
 * <p><b>caller-side filter (이서연 합의 2026-05-08)</b>: 어댑터의
 * {@code findRecent} JPQL이 {@code COALESCE(send_started_at, created_at) >= :since} 라
 * "예약만 되고 발송 안 된 회차" 도 반환할 수 있다. 그런 회차를 그대로 검증하면
 * expected = targetCount, actual = 0 으로 노이즈 불일치가 적재되므로, 본 스케줄러에서
 * {@code countByCampaignExecutionIdAndChannel > 0} 으로 한 번 거른 뒤 검증한다.
 * stale execution detection 은 별도 메트릭/이슈로 분리된다.
 *
 * <p>실패 시 본 스케줄러는 회차 1건의 예외에 전체 배치가 멈추지 않도록 try/catch 로 격리하고,
 * 실패 카운트만 로깅한다. 운영 모니터링은 {@code system_health_snapshots} (S14P31B101-187 PR-3)
 * 의 {@code reconciliation_inconsistent_count} 메트릭으로 누적된다.
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "butterfly.reconciliation.enabled", havingValue = "true")
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    /** {@code system_health_snapshots.metric_name} 으로 기록되는 카운트 메트릭 (PR-3). */
    public static final String METRIC_RECONCILIATION_INCONSISTENT_COUNT = "reconciliation_inconsistent_count";

    private final CampaignExecutionPort campaignExecutionPort;
    private final ReportingNotificationLogRepository notificationLogRepository;
    private final ReconciliationService reconciliationService;
    private final SystemHealthSnapshotRepository healthSnapshotRepository;

    /** WARN/CRITICAL 임계치는 환경별 조정 가능. 기본값은 운영 합의 전 임시 기준 (작은 값 → WARN 빨리). */
    @Value("${butterfly.reconciliation.health.warn-threshold:10}")
    private int warnThreshold;

    @Value("${butterfly.reconciliation.health.crit-threshold:100}")
    private int critThreshold;

    /**
     * 매일 02:00 KST 실행. since = 시작 시점 - 24h.
     *
     * <p>daily 롤업 (cron 0 0 1) 직후, hourly 워커 (5분 주기) 의 마지막 실행도 안정된 시점.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void runDailyReconciliation() {
        runFor(LocalDateTime.now().minusHours(24));
    }

    /**
     * 외부에서 since 를 주어 실행 (테스트 + 운영 트러블슈팅용).
     *
     * @return {@code int[]{checked, consistent, inconsistent, skipped, failed}}
     */
    public int[] runFor(LocalDateTime since) {
        long startMs = System.currentTimeMillis();
        List<CampaignExecutionInfo> infos = campaignExecutionPort.findRecent(since);

        int checked = 0;
        int consistent = 0;
        int inconsistent = 0;
        int skipped = 0;
        int failed = 0;

        for (CampaignExecutionInfo info : infos) {
            // caller-side filter: 실제 발송 row 가 1건이라도 있어야 의미있는 검증
            long actualRows = notificationLogRepository
                    .countByCampaignExecutionIdAndChannel(info.executionId(), info.channel());
            if (actualRows == 0) {
                skipped++;
                continue;
            }

            try {
                boolean ok = reconciliationService.reconcileInfo(info).isConsistent();
                checked++;
                if (ok) consistent++;
                else inconsistent++;
            } catch (RuntimeException ex) {
                failed++;
                log.error("reconciliation failed execution_id={} channel={} cause={}",
                        info.executionId(), info.channel(), ex.getMessage(), ex);
            }
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        log.info("reconciliation batch done since={} total={} checked={} consistent={} inconsistent={} skipped={} failed={} elapsedMs={}",
                since, infos.size(), checked, consistent, inconsistent, skipped, failed, elapsedMs);

        recordHealthMetric(inconsistent);

        return new int[]{checked, consistent, inconsistent, skipped, failed};
    }

    /**
     * 본 배치에서 발견된 불일치 카운트를 {@code system_health_snapshots} 에 적재한다.
     *
     * <p>본 PR(187 PR-3) 범위에서는 회당 누적값을 1행으로 INSERT — 시계열 분석 시 같은
     * {@code metric_name} 의 latest row 를 보면 직전 배치의 결과가 된다. failed 카운트가
     * 0보다 크면 임계치와 무관하게 WARN 으로 끌어올린다 (예외로 측정값 자체가 신뢰 불가).
     */
    private void recordHealthMetric(int inconsistentCount) {
        BigDecimal value = BigDecimal.valueOf(inconsistentCount);
        BigDecimal warn = BigDecimal.valueOf(warnThreshold);
        BigDecimal crit = BigDecimal.valueOf(critThreshold);
        HealthStatus status = HealthStatus.from(value, warn, crit);

        SystemHealthSnapshot snapshot = SystemHealthSnapshot.create()
                .metricName(METRIC_RECONCILIATION_INCONSISTENT_COUNT)
                .metricTarget(null) // 전사 카운트 — target 미지정
                .metricValue(value)
                .thresholdWarn(warn)
                .thresholdCrit(crit)
                .status(status)
                .collectedAt(LocalDateTime.now())
                .build();
        try {
            healthSnapshotRepository.save(snapshot);
        } catch (RuntimeException ex) {
            // 메트릭 적재 실패가 정합성 배치 자체를 중단시키지 않도록 격리.
            log.error("system_health_snapshots insert failed metric={} value={} cause={}",
                    METRIC_RECONCILIATION_INCONSISTENT_COUNT, value, ex.getMessage(), ex);
        }
    }
}
