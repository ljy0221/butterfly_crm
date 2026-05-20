package com.b101.butterfly.reporting.aggregation.service;

import com.b101.butterfly.reporting.aggregation.dto.DailyCampaignCountRow;
import com.b101.butterfly.reporting.aggregation.dto.DailyFailureCountRow;
import com.b101.butterfly.reporting.aggregation.dto.DailyProjectCountRow;
import com.b101.butterfly.reporting.aggregation.dto.DailyTemplateCountRow;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsHourlyRepository;
import com.b101.butterfly.reporting.aggregation.repository.FailureStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.ProjectStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.TemplateStatsDailyRepository;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 매일 새벽 1시 campaign_stats_hourly → 4종 daily 테이블 롤업 워커 (S14P31B101-191 / 124-B).
 *
 * <p><b>롤업 순서</b>:
 * <ol>
 *   <li>campaign_stats_daily — hourly 합산 + 비율 지표</li>
 *   <li>template_stats_daily — notification_logs GROUP BY template</li>
 *   <li>failure_stats_daily  — notification_logs FAILED GROUP BY error_code</li>
 *   <li>project_stats_daily  — campaign_stats_daily GROUP BY project</li>
 * </ol>
 *
 * <p><b>force 재집계</b>: force=true면 대상 날짜 row를 삭제 후 재집계.
 * 직전 7일 윈도우 재처리는 {@link #rollupRange}를 사용한다 (KPI 정의서 §5.2).
 *
 * <p><b>open/click/convert</b>: S14P31B101-123(user_action_logs) 머지 전까지 0으로 유지.
 */
@Service
@RequiredArgsConstructor
public class DailyRollupService {

    private static final Logger log = LoggerFactory.getLogger(DailyRollupService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CampaignStatsHourlyRepository hourlyRepository;
    private final CampaignStatsDailyRepository campaignDailyRepository;
    private final TemplateStatsDailyRepository templateDailyRepository;
    private final FailureStatsDailyRepository failureDailyRepository;
    private final ProjectStatsDailyRepository projectDailyRepository;
    private final ReportingNotificationLogRepository notificationLogRepository;

    // rollupRange → rollup 자기 호출 시 프록시 우회 방지용 self-bean 주입
    @Autowired
    @Lazy
    private DailyRollupService self;

    /**
     * 단일 날짜 롤업.
     *
     * @param date  집계 대상 날짜 (그 날 00:00 ~ 다음날 00:00)
     * @param force true면 기존 row 삭제 후 재집계
     * @return 각 테이블별 UPSERT 행 수를 담은 배열 [campaign, template, failure, project]
     */
    @Transactional
    public int[] rollup(LocalDate date, boolean force) {
        long startMs = System.currentTimeMillis();
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        if (force) {
            campaignDailyRepository.deleteByBucketDate(date);
            templateDailyRepository.deleteByBucketDate(date);
            failureDailyRepository.deleteByBucketDate(date);
            projectDailyRepository.deleteByBucketDate(date);
            log.info("daily rollup force=true cleared all rows for date={}", date);
        }

        int campaignRows = rollupCampaign(date, dayStart, dayEnd);
        int templateRows = rollupTemplate(date, dayStart, dayEnd);
        int failureRows = rollupFailure(date, dayStart, dayEnd);
        int projectRows = rollupProject(date);

        long elapsed = System.currentTimeMillis() - startMs;
        log.info("daily rollup done date={} campaign={} template={} failure={} project={} elapsedMs={}",
                date, campaignRows, templateRows, failureRows, projectRows, elapsed);

        return new int[]{campaignRows, templateRows, failureRows, projectRows};
    }

    /**
     * 직전 N일 범위 재집계. force=true인 경우 KPI 정의서 §5.2 기준 7일 윈도우.
     *
     * <p>@Transactional 미적용 — 날짜별로 self.rollup()을 호출해 각 날짜가 독립 트랜잭션으로
     * 처리되도록 한다. 직접 this.rollup() 호출 시 Spring AOP 프록시가 우회되어 7일치가
     * 단일 트랜잭션으로 묶이고 중간 실패 시 전체 롤백되는 문제를 방지한다.
     */
    public int[] rollupRange(LocalDate from, LocalDate to, boolean force) {
        int[] totals = new int[4];
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            int[] rows = self.rollup(cursor, force);
            for (int i = 0; i < totals.length; i++) {
                totals[i] += rows[i];
            }
            cursor = cursor.plusDays(1);
        }
        return totals;
    }

    /**
     * 매일 새벽 1시 KST 자동 실행 — 직전 7일(D-7 ~ D-1) 롤업 (KPI 정의서 §5.2).
     *
     * <p>zone = "Asia/Seoul" 명시 — JVM 기본 타임존이 UTC인 컨테이너에서도 KST 01:00에 발사.
     * LocalDate.now(KST) 명시로 JVM 기본 타임존에 무관하게 KST 날짜를 사용한다.
     * D-1 신규 집계 + D-2~D-7 재집계를 rollupRange 한 번으로 처리한다.
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void scheduledRollup() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        try {
            rollupRange(yesterday.minusDays(6), yesterday, false);
        } catch (Exception e) {
            log.error("daily rollup scheduled run failed for date={}", yesterday, e);
        }
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private int rollupCampaign(LocalDate date, LocalDateTime dayStart, LocalDateTime dayEnd) {
        List<DailyCampaignCountRow> rows = hourlyRepository.rollupByDate(dayStart, dayEnd);
        int upserted = 0;
        for (DailyCampaignCountRow r : rows) {
            upserted += campaignDailyRepository.upsert(
                    r.projectId(),
                    r.campaignId(),
                    r.channel().name(),
                    date,
                    safe(r.sentCount()),
                    safe(r.deliveredCount()),
                    safe(r.failedCount()),
                    safe(r.openCount()),
                    safe(r.clickCount()),
                    safe(r.convertCount())
            );
        }
        return upserted;
    }

    private int rollupTemplate(LocalDate date, LocalDateTime dayStart, LocalDateTime dayEnd) {
        List<DailyTemplateCountRow> rows = notificationLogRepository.aggregateTemplateDaily(dayStart, dayEnd);
        int upserted = 0;
        for (DailyTemplateCountRow r : rows) {
            upserted += templateDailyRepository.upsert(
                    r.projectId(),
                    r.messageTemplateId(),
                    r.channel().name(),
                    date,
                    safe(r.usedCampaignCount()),
                    safe(r.sentCount()),
                    safe(r.deliveredCount()),
                    safe(r.openCount()),
                    safe(r.clickCount()),
                    safe(r.convertCount())
            );
        }
        return upserted;
    }

    private int rollupFailure(LocalDate date, LocalDateTime dayStart, LocalDateTime dayEnd) {
        List<DailyFailureCountRow> rows = notificationLogRepository.aggregateFailureDaily(dayStart, dayEnd);
        int upserted = 0;
        for (DailyFailureCountRow r : rows) {
            upserted += failureDailyRepository.upsert(
                    r.projectId(),
                    r.channel().name(),
                    r.errorCode(),
                    date,
                    safe(r.failedCount()),
                    safe(r.affectedCampaignCnt()),
                    safe(r.affectedUserCnt()),
                    r.firstOccurredAt(),
                    r.lastOccurredAt()
            );
        }
        return upserted;
    }

    private int rollupProject(LocalDate date) {
        // campaign_stats_daily에서 project × date로 GROUP BY해 전사 집계
        List<DailyProjectCountRow> rows = campaignDailyRepository.rollupProjectByDate(date);
        int upserted = 0;
        for (DailyProjectCountRow r : rows) {
            upserted += projectDailyRepository.upsert(
                    r.projectId(),
                    date,
                    safe(r.totalSent()),
                    safe(r.totalDelivered()),
                    safe(r.totalFailed()),
                    safe(r.totalOpen()),
                    safe(r.totalClick()),
                    safe(r.totalConvert()),
                    safe(r.activeCampaignCount())
            );
        }
        return upserted;
    }

    private static int safe(Long v) {
        return v == null ? 0 : v.intValue();
    }
}
