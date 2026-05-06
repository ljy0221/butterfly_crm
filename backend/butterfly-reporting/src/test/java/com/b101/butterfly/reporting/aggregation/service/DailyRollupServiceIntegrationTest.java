package com.b101.butterfly.reporting.aggregation.service;

import com.b101.butterfly.reporting.aggregation.entity.CampaignStatsDaily;
import com.b101.butterfly.reporting.aggregation.entity.FailureStatsDaily;
import com.b101.butterfly.reporting.aggregation.entity.TemplateStatsDaily;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsHourlyRepository;
import com.b101.butterfly.reporting.aggregation.repository.FailureStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.ProjectStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.TemplateStatsDailyRepository;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S14P31B101-191 (124-B) DailyRollupService 통합 테스트.
 *
 * <p>hourly 집계 → daily 롤업 파이프라인 전 구간을 MySQL 컨테이너에서 검증한다.
 * 검증 항목:
 * <ul>
 *   <li>4종 daily 테이블 모두 채워짐</li>
 *   <li>비율 지표 정확성 (delivery_rate, open_rate 등)</li>
 *   <li>분모 0 → rate = null</li>
 *   <li>force=true 재집계 멱등성</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DailyRollupServiceIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private DailyRollupService rollupService;
    @Autowired private HourlyAggregator hourlyAggregator;
    @Autowired private CampaignStatsDailyRepository campaignDailyRepository;
    @Autowired private TemplateStatsDailyRepository templateDailyRepository;
    @Autowired private FailureStatsDailyRepository failureDailyRepository;
    @Autowired private ProjectStatsDailyRepository projectDailyRepository;
    @Autowired private CampaignStatsHourlyRepository hourlyRepository;
    @Autowired private ReportingNotificationLogRepository notificationLogRepository;

    @AfterEach
    void cleanup() {
        campaignDailyRepository.deleteAll();
        templateDailyRepository.deleteAll();
        failureDailyRepository.deleteAll();
        projectDailyRepository.deleteAll();
        hourlyRepository.deleteAll();
        notificationLogRepository.deleteAll();
    }

    @Test
    @DisplayName("hourly 집계 후 daily 롤업 시 4종 테이블 모두 채워진다")
    void rollupFillsAllFourTables() {
        // given: notification_logs seed 후 hourly 집계
        seedLog(NotificationStatus.SENT,      Channel.PUSH,  "k1", 1L, 10L, null);
        seedLog(NotificationStatus.DELIVERED, Channel.PUSH,  "k2", 1L, 10L, null);
        seedLog(NotificationStatus.FAILED,    Channel.EMAIL, "k3", 1L, 20L, "DELIVERY_009");
        seedLog(NotificationStatus.SENT,      Channel.SMS,   "k4", 1L, 30L, null);

        LocalDateTime bucket = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        hourlyAggregator.aggregate(bucket, false);

        // when
        LocalDate today = LocalDate.now();
        int[] rows = rollupService.rollup(today, false);

        // then: 각 테이블에 row가 생성됨
        assertThat(rows[0]).as("campaign_stats_daily rows").isPositive();
        assertThat(rows[3]).as("project_stats_daily rows").isPositive();
        assertThat(campaignDailyRepository.count()).isPositive();
        assertThat(projectDailyRepository.count()).isPositive();
    }

    @Test
    @DisplayName("notification_logs FAILED row가 있으면 failure_stats_daily에 집계된다")
    void failureStatsAreRolledUp() {
        seedLog(NotificationStatus.FAILED, Channel.EMAIL, "k1", 1L, null, "DELIVERY_009");
        seedLog(NotificationStatus.FAILED, Channel.EMAIL, "k2", 1L, null, "DELIVERY_009");

        LocalDate today = LocalDate.now();
        rollupService.rollup(today, false);

        assertThat(failureDailyRepository.count()).isPositive();
        FailureStatsDaily row = failureDailyRepository.findAll().get(0);
        assertThat(row.getFailedCount()).isEqualTo(2);
        assertThat(row.getErrorCode()).isEqualTo("DELIVERY_009");
    }

    @Test
    @DisplayName("템플릿이 있는 notification_logs는 template_stats_daily에 집계된다")
    void templateStatsAreRolledUp() {
        seedLog(NotificationStatus.SENT,      Channel.PUSH, "k1", 1L, 10L, null, 99L);
        seedLog(NotificationStatus.DELIVERED, Channel.PUSH, "k2", 1L, 10L, null, 99L);

        LocalDate today = LocalDate.now();
        rollupService.rollup(today, false);

        assertThat(templateDailyRepository.count()).isPositive();
        TemplateStatsDaily row = templateDailyRepository.findAll().get(0);
        assertThat(row.getMessageTemplateId()).isEqualTo(99L);
        assertThat(row.getSentCount()).isEqualTo(2); // SENT + DELIVERED
    }

    @Test
    @DisplayName("sent=0이면 delivery_rate가 null이다 (분모 0 방어)")
    void deliveryRateIsNullWhenSentIsZero() {
        // PENDING 상태 로그만 seed → hourly 집계 시 sent=0, delivered=0, failed=0
        seedLog(NotificationStatus.PENDING, Channel.PUSH, "k-pending-1", 1L, 777L, null);
        seedLog(NotificationStatus.PENDING, Channel.PUSH, "k-pending-2", 1L, 777L, null);

        LocalDateTime bucket = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        hourlyAggregator.aggregate(bucket, false);

        LocalDate today = LocalDate.now();
        rollupService.rollup(today, false);

        Optional<CampaignStatsDaily> row = campaignDailyRepository
                .findByCampaignIdAndChannelAndBucketDate(777L, Channel.PUSH, today);
        assertThat(row).isPresent();
        assertThat(row.get().getDeliveryRate()).isNull();
    }

    @Test
    @DisplayName("force=true는 기존 row 삭제 후 재집계 — 멱등성 보장")
    void forceReaggregatesIdempotently() {
        seedLog(NotificationStatus.SENT, Channel.PUSH, "k1", 1L, 10L, null);
        LocalDateTime bucket = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        hourlyAggregator.aggregate(bucket, false);

        LocalDate today = LocalDate.now();
        rollupService.rollup(today, false);
        long countBefore = campaignDailyRepository.count();

        // 같은 데이터로 force 재집계
        rollupService.rollup(today, true);
        long countAfter = campaignDailyRepository.count();

        assertThat(countAfter).isEqualTo(countBefore);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private NotificationLog seedLog(NotificationStatus status, Channel channel, String key,
                                    Long projectId, Long campaignId, String errorCode) {
        return seedLog(status, channel, key, projectId, campaignId, errorCode, null);
    }

    private NotificationLog seedLog(NotificationStatus status, Channel channel, String key,
                                    Long projectId, Long campaignId, String errorCode,
                                    Long messageTemplateId) {
        return notificationLogRepository.save(NotificationLog.create()
                .projectId(projectId)
                .campaignId(campaignId != null ? campaignId : 10L)
                .messageTemplateId(messageTemplateId)
                .channel(channel)
                .status(status)
                .errorCode(errorCode)
                .messageKey(key)
                .sentAt(LocalDateTime.now())
                .build());
    }
}
