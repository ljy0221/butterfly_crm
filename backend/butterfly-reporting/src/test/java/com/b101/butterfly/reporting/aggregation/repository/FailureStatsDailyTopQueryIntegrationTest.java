package com.b101.butterfly.reporting.aggregation.repository;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.FailureCountRow;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S14P31B101-33 — failure_stats_daily.findTopByProjectAndDate JPQL 검증.
 *
 * <p>고유 projectId로 다른 통합 테스트와 격리. JdbcTemplate 직접 INSERT.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FailureStatsDailyTopQueryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired
    private FailureStatsDailyRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long PROJECT_ID = 33_001L;
    private static final long PROJECT_ID_OTHER = 33_002L;
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 7);
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 5, 6);

    @BeforeEach
    @AfterEach
    void cleanIsolatedRange() {
        jdbcTemplate.update(
                "DELETE FROM failure_stats_daily WHERE project_id IN (?, ?)",
                PROJECT_ID, PROJECT_ID_OTHER);
    }

    private void insertRow(long projectId, Channel channel, String errorCode,
                           LocalDate bucketDate, int failedCount,
                           int affectedCampaignCnt, int affectedUserCnt) {
        jdbcTemplate.update(
                "INSERT INTO failure_stats_daily "
                        + "(project_id, channel, error_code, bucket_date, "
                        + " failed_count, affected_campaign_cnt, affected_user_cnt, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                projectId, channel.name(), errorCode, bucketDate,
                failedCount, affectedCampaignCnt, affectedUserCnt);
    }

    @Test
    @DisplayName("프로젝트·일자별 (channel, error_code) 단위로 failed_count DESC 정렬")
    void sortsByFailedCountDesc() {
        // UNIQUE 제약 (project_id, channel, error_code, bucket_date)이라 같은 키 중복 INSERT 불가.
        // 대신 서로 다른 (channel, error_code) 조합 3개로 정렬 검증.
        insertRow(PROJECT_ID, Channel.PUSH, "TOKEN_INVALID", TODAY, 150, 4, 120);
        insertRow(PROJECT_ID, Channel.EMAIL, "INVALID_ADDRESS", TODAY, 80, 2, 70);
        insertRow(PROJECT_ID, Channel.SMS, "RATE_LIMITED", TODAY, 30, 1, 25);

        // 다른 일자 — 제외
        insertRow(PROJECT_ID, Channel.PUSH, "TOKEN_INVALID", YESTERDAY, 999, 99, 999);
        // 다른 프로젝트 — 제외
        insertRow(PROJECT_ID_OTHER, Channel.PUSH, "TOKEN_INVALID", TODAY, 999, 99, 999);

        List<FailureCountRow> top = repository.findTopByProjectAndDate(
                PROJECT_ID, TODAY, PageRequest.of(0, 5));

        assertThat(top).hasSize(3);
        // 정렬: TOKEN_INVALID(150) > INVALID_ADDRESS(80) > RATE_LIMITED(30)
        assertThat(top.get(0).channel()).isEqualTo(Channel.PUSH);
        assertThat(top.get(0).errorCode()).isEqualTo("TOKEN_INVALID");
        assertThat(top.get(0).failedCount()).isEqualTo(150);
        assertThat(top.get(0).affectedCampaignCount()).isEqualTo(4);
        assertThat(top.get(0).affectedUserCount()).isEqualTo(120);
        assertThat(top.get(1).errorCode()).isEqualTo("INVALID_ADDRESS");
        assertThat(top.get(2).errorCode()).isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("같은 (channel, error_code)가 다른 row들에 분산되어 있을 때 SUM으로 합산")
    void sumsAcrossRowsWhenSchemaAllows() {
        // 실제로는 UNIQUE 제약이 같은 (project, channel, error_code, date) 중복을 막는다.
        // SUM 집계 의미가 살아 있는 케이스: 다른 channel은 같은 errorCode를 별도 row로 가짐.
        // 본 케이스는 GROUP BY (channel, errorCode)를 보장 — 같은 errorCode가 다른 channel에 있으면
        // 각 channel별 row로 분리되어 나옴.
        insertRow(PROJECT_ID, Channel.PUSH, "INVALID_ADDRESS", TODAY, 70, 2, 60);
        insertRow(PROJECT_ID, Channel.EMAIL, "INVALID_ADDRESS", TODAY, 30, 1, 25);

        List<FailureCountRow> top = repository.findTopByProjectAndDate(
                PROJECT_ID, TODAY, PageRequest.of(0, 5));

        assertThat(top).hasSize(2);
        // 같은 errorCode지만 channel이 달라 별도 row로 분리됨
        assertThat(top).extracting(FailureCountRow::channel)
                .containsExactly(Channel.PUSH, Channel.EMAIL);
        assertThat(top).extracting(FailureCountRow::failedCount)
                .containsExactly(70L, 30L);
    }

    @Test
    @DisplayName("limit 적용 — 결과 N개로 자름")
    void appliesLimit() {
        insertRow(PROJECT_ID, Channel.PUSH, "E1", TODAY, 100, 1, 1);
        insertRow(PROJECT_ID, Channel.PUSH, "E2", TODAY, 50, 1, 1);
        insertRow(PROJECT_ID, Channel.PUSH, "E3", TODAY, 30, 1, 1);

        List<FailureCountRow> top = repository.findTopByProjectAndDate(
                PROJECT_ID, TODAY, PageRequest.of(0, 2));

        assertThat(top).hasSize(2);
        assertThat(top).extracting(FailureCountRow::errorCode).containsExactly("E1", "E2");
    }

    @Test
    @DisplayName("매칭 row 없음 — 빈 list")
    void emptyWhenNoMatch() {
        List<FailureCountRow> top = repository.findTopByProjectAndDate(
                PROJECT_ID, TODAY, PageRequest.of(0, 5));
        assertThat(top).isEmpty();
    }
}
