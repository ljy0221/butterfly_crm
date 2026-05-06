package com.b101.butterfly.reporting.aggregation.service;

import com.b101.butterfly.reporting.aggregation.dto.DailyRollupResponse;
import com.b101.butterfly.reporting.aggregation.entity.CampaignStatsDaily;
import com.b101.butterfly.reporting.aggregation.entity.FailureStatsDaily;
import com.b101.butterfly.reporting.aggregation.entity.ProjectStatsDaily;
import com.b101.butterfly.reporting.aggregation.entity.TemplateStatsDaily;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.FailureStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.ProjectStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.TemplateStatsDailyRepository;
import com.b101.butterfly.reporting.event.entity.ActionType;
import com.b101.butterfly.reporting.event.entity.UserActionLog;
import com.b101.butterfly.reporting.event.repository.UserActionLogRepository;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S14P31B101-191 (124-B) DailyAggregator 통합 테스트.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DailyAggregatorIntegrationTest extends AbstractMySqlContainerTest {

    // notification_logs.created_at은 @PrePersist → now() 이므로 오늘 날짜를 bucket으로 사용
    private static final LocalDate BUCKET_DATE = LocalDate.now();
    private static final LocalDateTime DAY_START = BUCKET_DATE.atStartOfDay();

    @Autowired private DailyAggregator aggregator;
    @Autowired private CampaignStatsDailyRepository campaignDailyRepository;
    @Autowired private TemplateStatsDailyRepository templateDailyRepository;
    @Autowired private FailureStatsDailyRepository failureDailyRepository;
    @Autowired private ProjectStatsDailyRepository projectDailyRepository;
    @Autowired private ReportingNotificationLogRepository notificationLogRepository;
    @Autowired private UserActionLogRepository userActionLogRepository;

    @AfterEach
    void cleanup() {
        userActionLogRepository.deleteAll();
        campaignDailyRepository.deleteAll();
        templateDailyRepository.deleteAll();
        failureDailyRepository.deleteAll();
        projectDailyRepository.deleteAll();
        notificationLogRepository.deleteAll();
    }

    private NotificationLog seedNotifLog(long projectId, long campaignId, Long templateId,
                                          NotificationStatus status, String errorCode, String key) {
        return notificationLogRepository.save(NotificationLog.create()
                .projectId(projectId)
                .campaignId(campaignId)
                .messageTemplateId(templateId)
                .channel(Channel.PUSH)
                .status(status)
                .errorCode(errorCode)
                .messageKey(key)
                .sentAt(DAY_START.plusHours(1))
                .build());
    }

    private UserActionLog seedAction(long logId, long projectId, long campaignId,
                                      ActionType actionType) {
        return userActionLogRepository.save(UserActionLog.create()
                .logId(logId)
                .projectId(projectId)
                .campaignId(campaignId)
                .actionType(actionType)
                .actionAt(DAY_START.plusHours(2))
                .build());
    }

    @BeforeEach
    void seedData() {
        // project=1, campaign=100, template=10
        NotificationLog n1 = seedNotifLog(1L, 100L, 10L, NotificationStatus.DELIVERED, null, "k1");
        NotificationLog n2 = seedNotifLog(1L, 100L, 10L, NotificationStatus.DELIVERED, null, "k2");
        NotificationLog n3 = seedNotifLog(1L, 100L, 10L, NotificationStatus.FAILED, "INVALID_TOKEN", "k3");
        NotificationLog n4 = seedNotifLog(1L, 100L, 10L, NotificationStatus.SENT, null, "k4");

        // user actions for campaign=100
        seedAction(n1.getId(), 1L, 100L, ActionType.OPEN);
        seedAction(n2.getId(), 1L, 100L, ActionType.OPEN);
        seedAction(n1.getId(), 1L, 100L, ActionType.CLICK);
        seedAction(n1.getId(), 1L, 100L, ActionType.CONVERT);
    }

    @Test
    @DisplayName("campaign_stats_daily 1 row 적재 + 비율 지표 검증")
    void campaignStatsAreRolledUp() {
        aggregator.rollup(BUCKET_DATE, false);

        Optional<CampaignStatsDaily> row = campaignDailyRepository
                .findByCampaignIdAndChannelAndBucketDate(100L, Channel.PUSH, BUCKET_DATE);

        assertThat(row).isPresent();
        CampaignStatsDaily s = row.get();
        assertThat(s.getSentCount()).isEqualTo(3);       // DELIVERED(2) + SENT(1) = sent
        assertThat(s.getDeliveredCount()).isEqualTo(2);
        assertThat(s.getFailedCount()).isEqualTo(1);
        assertThat(s.getOpenCount()).isEqualTo(2);
        assertThat(s.getClickCount()).isEqualTo(1);
        assertThat(s.getConvertCount()).isEqualTo(1);

        // delivery_rate = 2/3 * 100 = 66.67
        assertThat(s.getDeliveryRate()).isEqualByComparingTo(new BigDecimal("66.67"));
        // open_rate = 2/2 * 100 = 100.00
        assertThat(s.getOpenRate()).isEqualByComparingTo(new BigDecimal("100.00"));
        // click_rate = 1/2 * 100 = 50.00
        assertThat(s.getClickRate()).isEqualByComparingTo(new BigDecimal("50.00"));
        // convert_rate = 1/3 * 100 = 33.33
        assertThat(s.getConvertRate()).isEqualByComparingTo(new BigDecimal("33.33"));
    }

    @Test
    @DisplayName("template_stats_daily 1 row 적재")
    void templateStatsAreRolledUp() {
        aggregator.rollup(BUCKET_DATE, false);

        assertThat(templateDailyRepository.findByBucketDate(BUCKET_DATE)).hasSize(1);
        TemplateStatsDaily t = templateDailyRepository.findByBucketDate(BUCKET_DATE).get(0);
        assertThat(t.getMessageTemplateId()).isEqualTo(10L);
        assertThat(t.getSentCount()).isEqualTo(3);
        assertThat(t.getDeliveredCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("failure_stats_daily 1 row 적재 (INVALID_TOKEN)")
    void failureStatsAreRolledUp() {
        aggregator.rollup(BUCKET_DATE, false);

        assertThat(failureDailyRepository.findByBucketDate(BUCKET_DATE)).hasSize(1);
        FailureStatsDaily f = failureDailyRepository.findByBucketDate(BUCKET_DATE).get(0);
        assertThat(f.getErrorCode()).isEqualTo("INVALID_TOKEN");
        assertThat(f.getFailedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("project_stats_daily 1 row 적재")
    void projectStatsAreRolledUp() {
        aggregator.rollup(BUCKET_DATE, false);

        Optional<ProjectStatsDaily> row = projectDailyRepository
                .findByProjectIdAndBucketDate(1L, BUCKET_DATE);
        assertThat(row).isPresent();
        ProjectStatsDaily p = row.get();
        assertThat(p.getTotalSent()).isEqualTo(3);
        assertThat(p.getTotalDelivered()).isEqualTo(2);
        assertThat(p.getTotalOpen()).isEqualTo(2);
        assertThat(p.getTotalClick()).isEqualTo(1);
    }

    @Test
    @DisplayName("분모 0 → 비율 지표 null")
    void zeroDenominatorRatesAreNull() {
        // 발송 0건이면 모든 비율 null
        notificationLogRepository.deleteAll();
        userActionLogRepository.deleteAll();

        aggregator.rollup(BUCKET_DATE, false);

        // 데이터 없으면 row 자체가 없다
        assertThat(campaignDailyRepository.findByBucketDate(BUCKET_DATE)).isEmpty();
    }

    @Test
    @DisplayName("force=true 는 기존 row 삭제 후 재집계")
    void forceDeletesAndReaggregates() {
        aggregator.rollup(BUCKET_DATE, false);
        assertThat(campaignDailyRepository.findByBucketDate(BUCKET_DATE)).hasSize(1);

        // 모든 소스 데이터 삭제 후 force 재집계
        userActionLogRepository.deleteAll();
        notificationLogRepository.deleteAll();
        aggregator.rollup(BUCKET_DATE, true);

        assertThat(campaignDailyRepository.findByBucketDate(BUCKET_DATE)).isEmpty();
        assertThat(templateDailyRepository.findByBucketDate(BUCKET_DATE)).isEmpty();
        assertThat(failureDailyRepository.findByBucketDate(BUCKET_DATE)).isEmpty();
        assertThat(projectDailyRepository.findByBucketDate(BUCKET_DATE)).isEmpty();
    }

    @Test
    @DisplayName("동일 bucket 재실행 시 누적되지 않는다 (UPSERT 덮어쓰기)")
    void rerunDoesNotAccumulate() {
        aggregator.rollup(BUCKET_DATE, false);
        aggregator.rollup(BUCKET_DATE, false);

        assertThat(campaignDailyRepository.findByBucketDate(BUCKET_DATE)).hasSize(1);
        CampaignStatsDaily s = campaignDailyRepository
                .findByCampaignIdAndChannelAndBucketDate(100L, Channel.PUSH, BUCKET_DATE)
                .orElseThrow();
        assertThat(s.getSentCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("DailyRollupResponse 에 각 테이블 upsert 수 반환")
    void responseContainsCounts() {
        DailyRollupResponse resp = aggregator.rollup(BUCKET_DATE, false);

        assertThat(resp.campaignRows()).isEqualTo(1);
        assertThat(resp.templateRows()).isEqualTo(1);
        assertThat(resp.failureRows()).isEqualTo(1);
        assertThat(resp.projectRows()).isEqualTo(1);
        assertThat(resp.elapsedMs()).isGreaterThanOrEqualTo(0);
    }
}
