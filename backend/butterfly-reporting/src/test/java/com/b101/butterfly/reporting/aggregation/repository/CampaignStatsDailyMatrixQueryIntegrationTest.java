package com.b101.butterfly.reporting.aggregation.repository;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.CampaignChannelStatsRow;
import com.b101.butterfly.reporting.AbstractReportingMySqlTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S14P31B101-92 — segment-channel 매트릭스가 사용하는 기간별 (campaign, channel) 합산
 * JPQL 검증. JdbcTemplate으로 직접 INSERT하여 entity manager 캐시 영향을 배제한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CampaignStatsDailyMatrixQueryIntegrationTest extends AbstractReportingMySqlTest {

    @Autowired
    private CampaignStatsDailyRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * MySQL testcontainer가 다른 테스트와 정적 공유되므로 각 테스트는 고유한 projectId를
     * 사용해 다른 테스트 데이터와 격리한다.
     */
    private static final long PROJECT_ID_AGGREGATE = 92_001L;
    private static final long PROJECT_ID_OTHER = 92_002L;
    private static final long PROJECT_ID_EMPTY = 92_999L;

    @BeforeEach
    @AfterEach
    void cleanIsolatedRange() {
        jdbcTemplate.update(
                "DELETE FROM campaign_stats_daily WHERE project_id IN (?, ?, ?)",
                PROJECT_ID_AGGREGATE, PROJECT_ID_OTHER, PROJECT_ID_EMPTY);
    }

    /** JdbcTemplate으로 직접 INSERT — repository.upsert의 native bind 영향 배제. */
    private void insertRow(long projectId, long campaignId, Channel channel, LocalDate bucketDate,
                           int sent, int delivered, int failed, int open, int click, int convert) {
        jdbcTemplate.update(
                "INSERT INTO campaign_stats_daily "
                        + "(project_id, campaign_id, channel, bucket_date, "
                        + " sent_count, delivered_count, failed_count, "
                        + " open_count, click_count, convert_count, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                projectId, campaignId, channel.name(), bucketDate,
                sent, delivered, failed, open, click, convert);
    }

    @Test
    @DisplayName("기간 내 (campaignId, channel) 단위로 합산되며 다른 프로젝트/기간 외 row는 제외된다")
    void aggregatesByCampaignAndChannelWithinDateRange() {
        long pid = PROJECT_ID_AGGREGATE;

        // (100, PUSH): 4/1 + 4/2 합산
        insertRow(pid, 100L, Channel.PUSH, LocalDate.of(2026, 4, 1), 100, 80, 5, 40, 10, 5);
        insertRow(pid, 100L, Channel.PUSH, LocalDate.of(2026, 4, 2), 200, 180, 10, 100, 30, 15);
        // (100, EMAIL): 별도 그룹
        insertRow(pid, 100L, Channel.EMAIL, LocalDate.of(2026, 4, 1), 50, 50, 0, 25, 5, 2);
        // (101, PUSH): 별도 그룹
        insertRow(pid, 101L, Channel.PUSH, LocalDate.of(2026, 4, 1), 10, 10, 0, 5, 1, 0);
        // 기간 밖 — 제외돼야 함
        insertRow(pid, 100L, Channel.PUSH, LocalDate.of(2026, 5, 5), 999, 999, 0, 999, 999, 999);
        // 다른 프로젝트 — 제외돼야 함 (UNIQUE constraint이 project_id를 포함하지 않으므로
        //   같은 (campaign_id, channel, bucket_date)는 충돌. 다른 campaignId로 회피.)
        insertRow(PROJECT_ID_OTHER, 9100L, Channel.PUSH, LocalDate.of(2026, 4, 1),
                999, 999, 0, 999, 999, 999);

        List<CampaignChannelStatsRow> rows = repository.aggregateByProjectAndDateRange(
                pid, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(rows).hasSize(3);

        CampaignChannelStatsRow campaign100Push = rows.stream()
                .filter(r -> r.campaignId().equals(100L) && r.channel() == Channel.PUSH)
                .findFirst().orElseThrow();
        assertThat(campaign100Push.sentCount()).isEqualTo(300);
        assertThat(campaign100Push.deliveredCount()).isEqualTo(260);
        assertThat(campaign100Push.openCount()).isEqualTo(140);
        assertThat(campaign100Push.clickCount()).isEqualTo(40);

        CampaignChannelStatsRow campaign100Email = rows.stream()
                .filter(r -> r.campaignId().equals(100L) && r.channel() == Channel.EMAIL)
                .findFirst().orElseThrow();
        assertThat(campaign100Email.sentCount()).isEqualTo(50);

        CampaignChannelStatsRow campaign101Push = rows.stream()
                .filter(r -> r.campaignId().equals(101L) && r.channel() == Channel.PUSH)
                .findFirst().orElseThrow();
        assertThat(campaign101Push.sentCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("기간 내 데이터 없음 → 빈 리스트")
    void emptyWhenNoData() {
        List<CampaignChannelStatsRow> rows = repository.aggregateByProjectAndDateRange(
                PROJECT_ID_EMPTY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(rows).isEmpty();
    }
}
