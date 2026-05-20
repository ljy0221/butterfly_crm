package com.b101.butterfly.reporting.aggregation.service;

import com.b101.butterfly.reporting.aggregation.entity.CampaignStatsHourly;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsHourlyRepository;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.reporting.AbstractReportingMySqlTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S14P31B101-124 (124-A) HourlyAggregator 통합 테스트.
 *
 * <p>seed 한 notification_logs row가 hourly UPSERT를 거쳐 campaign_stats_hourly의
 * 자연 키 row로 합산되는지, 동일 bucket 재실행 시 누적이 두 배가 되지 않는지(덮어쓰기),
 * force=true 동작이 의도대로 row 정리 후 재집계하는지 검증한다.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HourlyAggregatorIntegrationTest extends AbstractReportingMySqlTest {

    @Autowired private HourlyAggregator aggregator;
    @Autowired private CampaignStatsHourlyRepository hourlyRepository;
    @Autowired private ReportingNotificationLogRepository notificationLogRepository;

    @AfterEach
    void cleanup() {
        hourlyRepository.deleteAll();
        notificationLogRepository.deleteAll();
    }

    private NotificationLog seed(NotificationStatus status, String key) {
        return notificationLogRepository.save(NotificationLog.create()
                .projectId(123L)
                .campaignId(4521L)
                .channel(Channel.PUSH)
                .status(status)
                .messageKey(key)
                .sentAt(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("notification_logs 5건 seed → hourly aggregate → campaign_stats_hourly에 자연 키 row 1건 적재")
    void aggregatesToHourlyTable() {
        seed(NotificationStatus.SENT, "k1");
        seed(NotificationStatus.SENT, "k2");
        seed(NotificationStatus.DELIVERED, "k3");
        seed(NotificationStatus.FAILED, "k4");
        seed(NotificationStatus.PENDING, "k5");

        LocalDateTime bucket = currentBucket();
        int upserted = aggregator.aggregate(bucket, false);

        assertThat(upserted).isEqualTo(1);
        Optional<CampaignStatsHourly> row = hourlyRepository.findByCampaignIdAndChannelAndBucketHour(
                4521L, Channel.PUSH, bucket);
        assertThat(row).isPresent();
        CampaignStatsHourly stats = row.get();
        assertThat(stats.getProjectId()).isEqualTo(123L);
        assertThat(stats.getPendingCount()).isEqualTo(1);
        assertThat(stats.getSentCount()).isEqualTo(3); // SENT 2 + DELIVERED 1
        assertThat(stats.getDeliveredCount()).isEqualTo(1);
        assertThat(stats.getFailedCount()).isEqualTo(1);
        assertThat(stats.getOpenCount()).isZero();
        assertThat(stats.getClickCount()).isZero();
        assertThat(stats.getConvertCount()).isZero();
    }

    @Test
    @DisplayName("동일 bucket 재실행 시 카운트가 두 배로 누적되지 않는다 (덮어쓰기 정책)")
    void rerunDoesNotAccumulate() {
        seed(NotificationStatus.SENT, "k1");
        seed(NotificationStatus.SENT, "k2");

        LocalDateTime bucket = currentBucket();
        aggregator.aggregate(bucket, false);
        aggregator.aggregate(bucket, false);
        aggregator.aggregate(bucket, false);

        CampaignStatsHourly stats = hourlyRepository.findByCampaignIdAndChannelAndBucketHour(
                4521L, Channel.PUSH, bucket).orElseThrow();
        assertThat(stats.getSentCount())
                .as("UPSERT 덮어쓰기 — 누적되지 않고 항상 윈도우 카운트로 교체")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("force=true는 기존 bucket row 삭제 후 재집계")
    void forceClearsAndReaggregates() {
        // 첫 집계
        seed(NotificationStatus.SENT, "k1");
        seed(NotificationStatus.SENT, "k2");
        LocalDateTime bucket = currentBucket();
        aggregator.aggregate(bucket, false);

        // 모든 notification_logs 제거 후 force 재집계
        notificationLogRepository.deleteAll();

        aggregator.aggregate(bucket, true);

        // notification_logs가 없으므로 force 후에는 row 자체가 사라져야 한다
        Optional<CampaignStatsHourly> row = hourlyRepository.findByCampaignIdAndChannelAndBucketHour(
                4521L, Channel.PUSH, bucket);
        assertThat(row)
                .as("force는 기존 row를 삭제하고 새 윈도우에 데이터가 없으면 row를 만들지 않는다")
                .isEmpty();
    }

    @Test
    @DisplayName("notification_logs가 없으면 hourly UPSERT도 0건")
    void emptySourceProducesZeroUpserts() {
        int upserted = aggregator.aggregate(currentBucket(), false);

        assertThat(upserted).isZero();
        assertThat(hourlyRepository.count()).isZero();
    }

    private static LocalDateTime currentBucket() {
        return LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
    }
}
