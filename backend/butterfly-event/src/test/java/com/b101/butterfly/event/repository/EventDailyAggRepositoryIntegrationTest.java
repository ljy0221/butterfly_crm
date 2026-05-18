package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.EventDailyAgg;
import com.b101.butterfly.event.dto.response.EventCategoryStatsRow;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventDailyAggRepositoryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private EventDailyAggRepository repository;
    @Autowired private EntityManager entityManager;

    private static final LocalDate LOG_DATE = LocalDate.of(2026, 4, 30);
    private static final LocalDateTime AT_NOON = LocalDateTime.of(2026, 4, 30, 12, 0);
    private static final LocalDateTime AT_AFTERNOON = LocalDateTime.of(2026, 4, 30, 15, 30);
    private static final LocalDateTime AT_MORNING = LocalDateTime.of(2026, 4, 30, 9, 0);

    private void upsert(LocalDate date, Long projectId, Long categoryId, Long userId, LocalDateTime at) {
        repository.upsertIncrement(date, projectId, categoryId, userId, at);
    }

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("upsertIncrement — 신규 키는 새 행 insert (count=1, first_at=last_at=occurredAt)")
    void firstUpsertInsertsRowWithCountOne() {
        upsert(LOG_DATE, 100L, 1L, 7L, AT_NOON);
        reload();

        List<EventDailyAgg> all = repository.findAll();
        assertThat(all).hasSize(1);
        EventDailyAgg row = all.get(0);
        assertThat(row.getEventCount()).isEqualTo(1);
        assertThat(row.getFirstAt()).isEqualTo(AT_NOON);
        assertThat(row.getLastAt()).isEqualTo(AT_NOON);
    }

    @Test
    @DisplayName("upsertIncrement — 같은 키 재호출 시 count +1, last_at 갱신 (이후 시각)")
    void repeatedUpsertIncrementsCountAndAdvancesLastAt() {
        upsert(LOG_DATE, 100L, 1L, 7L, AT_NOON);
        upsert(LOG_DATE, 100L, 1L, 7L, AT_AFTERNOON);
        reload();

        EventDailyAgg row = repository.findAll().get(0);
        assertThat(row.getEventCount()).isEqualTo(2);
        assertThat(row.getFirstAt()).isEqualTo(AT_NOON);
        assertThat(row.getLastAt()).isEqualTo(AT_AFTERNOON);
    }

    @Test
    @DisplayName("upsertIncrement — out-of-order 이벤트(이전 시각)는 last_at 후퇴 안 함 (GREATEST)")
    void outOfOrderUpsertDoesNotRegressLastAt() {
        upsert(LOG_DATE, 100L, 1L, 7L, AT_NOON);
        upsert(LOG_DATE, 100L, 1L, 7L, AT_MORNING);
        reload();

        EventDailyAgg row = repository.findAll().get(0);
        assertThat(row.getEventCount()).isEqualTo(2);
        assertThat(row.getLastAt()).isEqualTo(AT_NOON);
    }

    @Test
    @DisplayName("upsertIncrement — 다른 (date, project, category, user) 조합은 별개 행")
    void differentKeysCreateSeparateRows() {
        upsert(LOG_DATE, 100L, 1L, 7L, AT_NOON);
        upsert(LOG_DATE, 100L, 1L, 8L, AT_NOON); // 다른 user
        upsert(LOG_DATE, 100L, 2L, 7L, AT_NOON); // 다른 category
        upsert(LOG_DATE, 200L, 1L, 7L, AT_NOON); // 다른 project
        upsert(LOG_DATE.plusDays(1), 100L, 1L, 7L, AT_NOON); // 다른 date
        reload();

        assertThat(repository.findAll()).hasSize(5);
    }

    @Test
    @DisplayName("aggregateCategoryStats — 최근 30일 수집량은 합산하고 마지막 수신은 전체 기간에서 계산")
    void aggregateCategoryStats_sumsWindowCountAndKeepsAllTimeLastCollectedAt() {
        LocalDate fromDate = LocalDate.of(2026, 4, 1);
        LocalDate oldDate = fromDate.minusDays(1);
        LocalDate recentDate = fromDate.plusDays(1);

        upsert(recentDate, 100L, 1L, 7L, LocalDateTime.of(2026, 4, 2, 10, 0));
        upsert(recentDate, 100L, 1L, 8L, LocalDateTime.of(2026, 4, 2, 11, 0));
        upsert(oldDate, 100L, 1L, 7L, LocalDateTime.of(2026, 3, 31, 23, 0));
        upsert(oldDate, 100L, 2L, 7L, LocalDateTime.of(2026, 3, 31, 9, 0));
        reload();

        List<EventCategoryStatsRow> stats =
                repository.aggregateCategoryStats(100L, List.of(1L, 2L), fromDate);

        EventCategoryStatsRow category1 = stats.stream()
                .filter(s -> s.categoryId().equals(1L))
                .findFirst()
                .orElseThrow();
        EventCategoryStatsRow category2 = stats.stream()
                .filter(s -> s.categoryId().equals(2L))
                .findFirst()
                .orElseThrow();

        assertThat(category1.eventCount()).isEqualTo(2L);
        assertThat(category1.lastCollectedAt()).isEqualTo(LocalDateTime.of(2026, 4, 2, 11, 0));
        assertThat(category2.eventCount()).isZero();
        assertThat(category2.lastCollectedAt()).isEqualTo(LocalDateTime.of(2026, 3, 31, 9, 0));
    }
}
