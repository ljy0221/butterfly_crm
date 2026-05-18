package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.response.EventCategoryDetailResponse;
import com.b101.butterfly.event.dto.response.EventCategorySummaryResponse;
import com.b101.butterfly.event.dto.response.EventCategoryStatsRow;
import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.EventCategoryType;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.event.repository.EventDailyAggRepository;
import com.b101.butterfly.event.repository.EventRepository;
import com.b101.butterfly.event.repository.EventSchemaFieldRepository;
import com.b101.butterfly.event.repository.EventSchemaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCategoryServiceTest {

    @Mock private EventCategoryRepository categoryRepository;
    @Mock private EventSchemaRepository schemaRepository;
    @Mock private EventSchemaFieldRepository fieldRepository;
    @Mock private EventDailyAggRepository dailyAggRepository;
    @Mock private EventRepository eventRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private EventCategoryService service;

    private static final Long PROJECT_ID = 3L;
    private static final LocalDateTime LAST_COLLECTED_AT = LocalDateTime.of(2026, 5, 18, 12, 58, 2);

    @Test
    @DisplayName("list — event_daily_agg 집계를 eventCount/lastCollectedAt에 반영")
    void listReflectsCategoryStats() {
        EventCategory productView = category(2L, "product_view");
        EventCategory purchase = category(3L, "purchase");

        when(categoryRepository.findAllByProjectIdOrderByNameAsc(PROJECT_ID))
                .thenReturn(List.of(productView, purchase));
        when(schemaRepository.findByCategoryIdAndIsCurrentTrue(any())).thenReturn(Optional.empty());
        when(dailyAggRepository.aggregateCategoryStats(eq(PROJECT_ID), eq(List.of(2L, 3L)), any(LocalDate.class)))
                .thenReturn(List.of(stats(2L, 20L, LAST_COLLECTED_AT)));

        List<EventCategorySummaryResponse> response = service.list(PROJECT_ID);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).categoryName()).isEqualTo("product_view");
        assertThat(response.get(0).eventCount()).isEqualTo(20L);
        assertThat(response.get(0).lastCollectedAt()).isEqualTo("2026-05-18T12:58:02");
        assertThat(response.get(1).categoryName()).isEqualTo("purchase");
        assertThat(response.get(1).eventCount()).isZero();
        assertThat(response.get(1).lastCollectedAt()).isNull();
    }

    @Test
    @DisplayName("get — 단건 상세에도 event_daily_agg 집계를 반영")
    void getReflectsCategoryStats() {
        EventCategory productView = category(2L, "product_view");

        when(categoryRepository.findByProjectIdAndName(PROJECT_ID, "product_view"))
                .thenReturn(Optional.of(productView));
        when(dailyAggRepository.aggregateCategoryStats(eq(PROJECT_ID), eq(List.of(2L)), any(LocalDate.class)))
                .thenReturn(List.of(stats(2L, 20L, LAST_COLLECTED_AT)));

        EventCategoryDetailResponse response = service.get(PROJECT_ID, "product_view");

        assertThat(response.categoryName()).isEqualTo("product_view");
        assertThat(response.eventCount()).isEqualTo(20L);
        assertThat(response.lastCollectedAt()).isEqualTo("2026-05-18T12:58:02");
    }

    private EventCategory category(Long categoryId, String name) {
        EventCategory category = EventCategory.builder()
                .projectId(PROJECT_ID)
                .name(name)
                .type(EventCategoryType.CUSTOM)
                .description(name + " display")
                .build();
        ReflectionTestUtils.setField(category, "id", categoryId);
        return category;
    }

    private EventCategoryStatsRow stats(Long categoryId, Long eventCount, LocalDateTime lastCollectedAt) {
        return new EventCategoryStatsRow(categoryId, eventCount, lastCollectedAt);
    }
}
