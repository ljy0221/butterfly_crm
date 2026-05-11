package com.b101.butterfly.reporting.reconciliation.service;

import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionInfo;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final CampaignExecutionPort campaignExecutionPort;
    private final ReportingNotificationLogRepository notificationLogRepository;
    private final ReconciliationService reconciliationService;

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

        return new int[]{checked, consistent, inconsistent, skipped, failed};
    }
}
