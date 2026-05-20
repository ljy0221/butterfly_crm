package com.b101.butterfly.reporting.aggregation.service;

import com.b101.butterfly.reporting.aggregation.dto.HourlyCountRow;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsHourlyRepository;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 5분 주기로 직전 5분 윈도우의 notification_logs를 캠페인×채널 단위로 합산해
 * `campaign_stats_hourly`에 UPSERT하는 hourly 집계 워커 (S14P31B101-124 / 124-A).
 *
 * <p><b>버킷팅</b>: 시간 단위 bucket(`bucket_hour`)에 같은 시간대의 5분 윈도우들이 함께 들어가도록
 * 윈도우 카운트를 매번 산정해 UPSERT. 같은 시간대(bucket)에 5분 단위 워커가 12번 도는 동안 마지막
 * 호출의 카운트가 살아있다 — `field = VALUES(field)` 덮어쓰기 정책이라 단일 호출 결과만 반영된다.
 *
 * <p>주의: 본 모델은 5분 윈도우가 매번 **누적이 아닌 절대 카운트** 를 만들어내야 정확하다 —
 * 즉 한 시간대(bucket) 안의 모든 윈도우를 누적하려면 윈도우 시작점을 bucket 시작으로 잡아 매번
 * 시간 시작부터 현재까지의 row를 카운트해야 한다. 본 구현은 124-A 단계의 단순 버전으로,
 * 윈도우=bucket 시작~현재 로 설정해 누적 카운트를 만든다.
 */
@Service
@EnableScheduling
@RequiredArgsConstructor
public class HourlyAggregator {

    private static final Logger log = LoggerFactory.getLogger(HourlyAggregator.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ReportingNotificationLogRepository notificationLogRepository;
    private final CampaignStatsHourlyRepository hourlyRepository;

    // scheduledAggregate → aggregate 자기 호출 시 CGLIB 프록시 우회 방지용 self-bean
    @Autowired
    @Lazy
    private HourlyAggregator self;

    /**
     * 단일 bucket 집계 트리거.
     *
     * @param bucketHour bucket의 시작 시각(분/초/나노=0)
     * @param force      true면 기존 bucket의 모든 row를 삭제 후 재집계
     * @return UPSERT 처리된 row 수
     */
    @Transactional
    public int aggregate(LocalDateTime bucketHour, boolean force) {
        long startMs = System.currentTimeMillis();
        LocalDateTime bucketStart = bucketHour.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime bucketEnd = bucketStart.plusHours(1);

        if (force) {
            int deleted = hourlyRepository.deleteByBucketHour(bucketStart);
            log.info("hourly aggregate force=true cleared {} rows for bucket={}",
                    deleted, bucketStart);
        }

        // 윈도우=[bucketStart, now()) — bucket 시작부터 현재까지의 누적 카운트로 UPSERT.
        // bucket이 끝난 후 호출되면 [bucketStart, bucketEnd)로 범위가 잡힌다.
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime windowEnd = now.isBefore(bucketEnd) ? now : bucketEnd;

        List<HourlyCountRow> rows = notificationLogRepository.aggregateHourly(bucketStart, windowEnd);

        int upserted = 0;
        for (HourlyCountRow row : rows) {
            upserted += hourlyRepository.upsert(
                    row.projectId(),
                    row.campaignId(),
                    row.channel().name(),
                    bucketStart,
                    safe(row.pendingCount()),
                    safe(row.sentCount()),
                    safe(row.deliveredCount()),
                    safe(row.failedCount())
            );
        }
        long elapsed = System.currentTimeMillis() - startMs;
        log.info("hourly aggregate done bucket={} rows={} elapsedMs={}",
                bucketStart, upserted, elapsed);
        return upserted;
    }

    /**
     * 5분 주기 자동 트리거. 현재 시각이 속한 시간대의 bucket을 집계한다.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 5 * 60 * 1000L)
    public void scheduledAggregate() {
        try {
            self.aggregate(currentBucket(), false);
        } catch (Exception e) {
            // 실패해도 다음 5분 후 재시도. 운영 알림은 system_health_snapshots(124-B)에서.
            log.error("hourly aggregate scheduled run failed", e);
        }
    }

    private LocalDateTime currentBucket() {
        LocalDateTime now = LocalDateTime.now(KST);
        return now.withMinute(0).withSecond(0).withNano(0);
    }

    private static int safe(Long count) {
        return count == null ? 0 : count.intValue();
    }

    /** 외부에서 윈도우 길이 참조용. 124-A는 5분 고정. */
    public static Duration windowSize() {
        return Duration.ofMinutes(5);
    }
}
