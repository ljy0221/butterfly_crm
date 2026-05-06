package com.b101.butterfly.reporting.aggregation.service;

import com.b101.butterfly.reporting.aggregation.dto.DailyActionCountRow;
import com.b101.butterfly.reporting.aggregation.dto.DailyCampaignCountRow;
import com.b101.butterfly.reporting.aggregation.dto.DailyFailureCountRow;
import com.b101.butterfly.reporting.aggregation.dto.DailyRollupResponse;
import com.b101.butterfly.reporting.aggregation.dto.DailyTemplateCountRow;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.FailureStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.ProjectStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.TemplateStatsDailyRepository;
import com.b101.butterfly.reporting.event.repository.UserActionLogRepository;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 매일 새벽 1시 직전 날의 집계를 4개 테이블에 롤업하는 daily 집계 워커 (S14P31B101-191 / 124-B).
 *
 * <p>KPI 정의서 §3 비율 공식:
 * <ul>
 *     <li>delivery_rate = delivered / sent × 100</li>
 *     <li>open_rate = open / delivered × 100</li>
 *     <li>click_rate = click / open × 100 (click/delivered와 다름 — §3.2)</li>
 *     <li>convert_rate = convert / sent × 100</li>
 * </ul>
 * 분모 0이면 null.
 *
 * <p>force=true 시 대상 bucket_date의 모든 row를 삭제 후 재집계한다 — KPI 정의서 §5.2 직전 7일 재집계 정책.
 */
@Service
@EnableScheduling
@RequiredArgsConstructor
public class DailyAggregator {

    private static final Logger log = LoggerFactory.getLogger(DailyAggregator.class);

    private final ReportingNotificationLogRepository notificationLogRepository;
    private final UserActionLogRepository userActionLogRepository;
    private final CampaignStatsDailyRepository campaignDailyRepository;
    private final TemplateStatsDailyRepository templateDailyRepository;
    private final FailureStatsDailyRepository failureDailyRepository;
    private final ProjectStatsDailyRepository projectDailyRepository;

    /**
     * 단일 bucket_date 롤업.
     *
     * @param bucketDate 집계 대상 날짜
     * @param force      true면 해당 날짜의 기존 row를 먼저 삭제 후 재집계
     */
    @Transactional
    public DailyRollupResponse rollup(LocalDate bucketDate, boolean force) {
        long startMs = System.currentTimeMillis();

        if (force) {
            int c = campaignDailyRepository.deleteByBucketDate(bucketDate);
            int t = templateDailyRepository.deleteByBucketDate(bucketDate);
            int f = failureDailyRepository.deleteByBucketDate(bucketDate);
            int p = projectDailyRepository.deleteByBucketDate(bucketDate);
            log.info("daily rollup force cleared campaign={} template={} failure={} project={} for date={}",
                    c, t, f, p, bucketDate);
        }

        LocalDateTime dayStart = bucketDate.atStartOfDay();
        LocalDateTime dayEnd = bucketDate.plusDays(1).atStartOfDay();

        // --- campaign stats ---
        List<DailyCampaignCountRow> campaignNotifRows =
                notificationLogRepository.aggregateDailyCampaign(dayStart, dayEnd);
        List<DailyActionCountRow> actionRows =
                userActionLogRepository.aggregateDailyActions(dayStart, dayEnd);

        // campaignId → action 카운트 맵
        Map<Long, DailyActionCountRow> actionMap = actionRows.stream()
                .collect(Collectors.toMap(DailyActionCountRow::campaignId, r -> r));

        int campaignUpserted = 0;
        for (DailyCampaignCountRow nr : campaignNotifRows) {
            DailyActionCountRow ar = actionMap.getOrDefault(nr.campaignId(),
                    new DailyActionCountRow(nr.campaignId(), 0L, 0L, 0L));

            int sent = safe(nr.sentCount());
            int delivered = safe(nr.deliveredCount());
            int failed = safe(nr.failedCount());
            int open = safe(ar.openCount());
            int click = safe(ar.clickCount());
            int convert = safe(ar.convertCount());

            campaignUpserted += campaignDailyRepository.upsert(
                    nr.projectId(), nr.campaignId(), nr.channel().name(), bucketDate,
                    sent, delivered, failed, open, click, convert,
                    rate(delivered, sent), rate(open, delivered),
                    rate(click, open), rate(convert, sent)
            );
        }

        // --- template stats ---
        List<DailyTemplateCountRow> templateRows =
                notificationLogRepository.aggregateDailyTemplate(dayStart, dayEnd);
        int templateUpserted = 0;
        for (DailyTemplateCountRow tr : templateRows) {
            int sent = safe(tr.sentCount());
            int delivered = safe(tr.deliveredCount());
            int open = safe(tr.openCount());
            int click = safe(tr.clickCount());
            int convert = safe(tr.convertCount());
            int usedCampaigns = safe(tr.usedCampaignCount());

            templateUpserted += templateDailyRepository.upsert(
                    tr.projectId(), tr.messageTemplateId(), tr.channel().name(), bucketDate,
                    usedCampaigns, sent, delivered, open, click, convert,
                    rate(open, delivered), rate(click, open), rate(convert, sent)
            );
        }

        // --- failure stats ---
        List<DailyFailureCountRow> failureRows =
                notificationLogRepository.aggregateDailyFailure(dayStart, dayEnd);
        int failureUpserted = 0;
        for (DailyFailureCountRow fr : failureRows) {
            failureUpserted += failureDailyRepository.upsert(
                    fr.projectId(), fr.channel().name(), fr.errorCode(), bucketDate,
                    safe(fr.failedCount()), safe(fr.affectedCampaignCnt()),
                    safe(fr.affectedUserCnt()), fr.firstOccurredAt(), fr.lastOccurredAt()
            );
        }

        // --- project stats: campaign rows를 project 단위로 fold ---
        int projectUpserted = rollupProjectStats(bucketDate, campaignNotifRows, actionMap);

        long elapsed = System.currentTimeMillis() - startMs;
        log.info("daily rollup done date={} campaign={} template={} failure={} project={} elapsedMs={}",
                bucketDate, campaignUpserted, templateUpserted, failureUpserted, projectUpserted, elapsed);

        return new DailyRollupResponse(campaignUpserted, templateUpserted, failureUpserted,
                projectUpserted, elapsed);
    }

    /**
     * 매일 새벽 1시 자동 트리거. 전날(KST)을 집계 대상으로 한다.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 1 * * *")
    public void scheduledRollup() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            rollup(yesterday, false);
        } catch (Exception e) {
            log.error("daily rollup scheduled run failed for date={}", yesterday, e);
        }
    }

    private int rollupProjectStats(LocalDate bucketDate,
                                    List<DailyCampaignCountRow> campaignRows,
                                    Map<Long, DailyActionCountRow> actionMap) {
        // projectId 단위로 합산
        Map<Long, long[]> projectTotals = new java.util.HashMap<>();
        for (DailyCampaignCountRow nr : campaignRows) {
            long[] totals = projectTotals.computeIfAbsent(nr.projectId(), k -> new long[6]);
            DailyActionCountRow ar = actionMap.getOrDefault(nr.campaignId(),
                    new DailyActionCountRow(nr.campaignId(), 0L, 0L, 0L));
            totals[0] += safe(nr.sentCount());
            totals[1] += safe(nr.deliveredCount());
            totals[2] += safe(nr.failedCount());
            totals[3] += safe(ar.openCount());
            totals[4] += safe(ar.clickCount());
            totals[5] += safe(ar.convertCount());
        }

        int upserted = 0;
        for (Map.Entry<Long, long[]> entry : projectTotals.entrySet()) {
            long[] t = entry.getValue();
            int sent = (int) t[0], delivered = (int) t[1], failed = (int) t[2];
            int open = (int) t[3], click = (int) t[4], convert = (int) t[5];

            upserted += projectDailyRepository.upsert(
                    entry.getKey(), bucketDate,
                    sent, delivered, failed, open, click, convert,
                    rate(open, delivered), rate(click, open), rate(convert, sent)
            );
        }
        return upserted;
    }

    /**
     * KPI 정의서 §3 공식: numerator / denominator × 100, 소수점 2자리, 분모 0이면 null.
     */
    static BigDecimal rate(int numerator, int denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static int safe(Long count) {
        return count == null ? 0 : count.intValue();
    }
}
