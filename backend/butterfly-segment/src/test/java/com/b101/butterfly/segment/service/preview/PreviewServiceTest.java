package com.b101.butterfly.segment.service.preview;

import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.validation.ConditionTreeValidator;
import com.b101.butterfly.segment.dto.response.PreviewResponse;
import com.b101.butterfly.segment.port.EventAggReader;
import com.b101.butterfly.segment.port.EventQueryContext;
import com.b101.butterfly.segment.port.UserDirectoryReader;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreviewServiceTest {

    @Mock private ConditionTreeValidator conditionTreeValidator;
    @Mock private ConditionTreeToSqlConverter sqlConverter;
    @Mock private UserDirectoryReader userDirectoryReader;
    @Mock private EventAggReader eventAggReader;
    @Mock private HllPreviewExecutor hllPreviewExecutor;
    @Mock private ConditionTreeHasher hasher;

    @InjectMocks private PreviewService previewService;

    private static final Long PROJECT_ID = 1L;
    private static final String HASH = "abc123";

    private static GroupNode tagOnlyTree() {
        return new GroupNode(
                LogicOp.AND,
                List.of(
                        new TagNode(
                                "region",
                                Operator.EQ,
                                JsonNodeFactory.instance.textNode("서울"))));
    }

    private static EventNode eventNode() {
        return new EventNode(99L, List.of(), new Period.Relative(7), 5);
    }

    private static EventNode filteredEventNode() {
        return new EventNode(
                99L,
                List.of(
                        new EventFilter(
                                "productId",
                                Operator.EQ,
                                JsonNodeFactory.instance.textNode("SKU-1"))),
                new Period.Relative(7),
                5);
    }

    private static GroupNode eventOnlyTree() {
        return new GroupNode(LogicOp.AND, List.of(eventNode()));
    }

    private static GroupNode filteredEventOnlyTree() {
        return new GroupNode(LogicOp.AND, List.of(filteredEventNode()));
    }

    private static GroupNode mixedTree() {
        return new GroupNode(
                LogicOp.AND,
                List.of(
                        new TagNode("region", Operator.EQ, JsonNodeFactory.instance.textNode("서울")),
                        eventNode()));
    }

    @Test
    @DisplayName("SQL path 캐시 hit — exact 캐시에서 EXACT 응답 (S14P31B101-275)")
    void cacheHitSkipsComputation() {
        when(hasher.hash(eq(PROJECT_ID), any())).thenReturn(HASH);
        when(hllPreviewExecutor.getCachedExact(HASH)).thenReturn(OptionalLong.of(1820L));

        PreviewResponse response = previewService.preview(PROJECT_ID, tagOnlyTree());

        assertThat(response.estimatedCount()).isEqualTo(1820);
        assertThat(response.method()).isEqualTo("EXACT");
        verify(sqlConverter, never()).convert(any(), any(EventQueryContext.class));
        verify(userDirectoryReader, never()).countUserMatches(anyString(), anyMap());
        verify(eventAggReader, never()).queryMatchingUserIds(any(), any());
        verify(hllPreviewExecutor, never()).cacheExactCount(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("TAG/USER/DEVICE 트리: SQL 변환 → countUserMatches → exact 캐시 (S14P31B101-275)")
    void cacheMissComputesAndCachesViaSql() {
        GroupNode tree = tagOnlyTree();
        when(hasher.hash(eq(PROJECT_ID), any())).thenReturn(HASH);
        when(hllPreviewExecutor.getCachedExact(HASH)).thenReturn(OptionalLong.empty());
        SqlFragment fragment = new SqlFragment("SELECT u.user_id FROM users u", Map.of());
        when(sqlConverter.convert(any(), any(EventQueryContext.class))).thenReturn(fragment);
        when(userDirectoryReader.countUserMatches(eq(fragment.sql()), eq(fragment.params())))
                .thenReturn(3L);

        PreviewResponse response = previewService.preview(PROJECT_ID, tree);

        assertThat(response.estimatedCount()).isEqualTo(3);
        verify(conditionTreeValidator).validate(tree);
        verify(eventAggReader, never()).queryMatchingUserIds(any(), any());
        verify(userDirectoryReader, never()).queryUserIds(anyString(), anyMap());
        verify(hllPreviewExecutor).cacheExactCount(eq(HASH), eq(3L), any());
    }

    @Test
    @DisplayName("EVENT 단독 트리: EventAggReader port 호출 → HLL 캐시 (sqlConverter 미호출)")
    void eventOnlyTreeUsesEventPort() {
        GroupNode tree = eventOnlyTree();
        when(hasher.hash(eq(PROJECT_ID), any())).thenReturn(HASH);
        when(hllPreviewExecutor.getCached(HASH)).thenReturn(Optional.empty());
        when(eventAggReader.queryMatchingUserIds(any(EventNode.class), any(EventQueryContext.class)))
                .thenReturn(Stream.of(10L, 20L));
        when(hllPreviewExecutor.computeAndCache(eq(HASH), eq(List.of(10L, 20L)), any()))
                .thenReturn(2L);

        PreviewResponse response = previewService.preview(PROJECT_ID, tree);

        assertThat(response.estimatedCount()).isEqualTo(2);
        verify(sqlConverter, never()).convert(any(), any(EventQueryContext.class));
        verify(userDirectoryReader, never()).queryUserIds(anyString(), anyMap());
    }

    @Test
    @DisplayName("필터가 있는 EVENT 단독 트리: SQL 변환 → countUserMatches (S14P31B101-275)")
    void filteredEventOnlyTreeUsesSqlConverter() {
        GroupNode tree = filteredEventOnlyTree();
        when(hasher.hash(eq(PROJECT_ID), any())).thenReturn(HASH);
        when(hllPreviewExecutor.getCachedExact(HASH)).thenReturn(OptionalLong.empty());
        SqlFragment fragment = new SqlFragment("SELECT u.user_id FROM users u WHERE filtered_event", Map.of());
        when(sqlConverter.convert(eq(tree), any(EventQueryContext.class))).thenReturn(fragment);
        when(userDirectoryReader.countUserMatches(eq(fragment.sql()), eq(fragment.params())))
                .thenReturn(2L);

        PreviewResponse response = previewService.preview(PROJECT_ID, tree);

        assertThat(response.estimatedCount()).isEqualTo(2);
        verify(eventAggReader, never()).queryMatchingUserIds(any(), any());
        verify(sqlConverter).convert(eq(tree), any(EventQueryContext.class));
        verify(hllPreviewExecutor).cacheExactCount(eq(HASH), eq(2L), any());
    }

    @Test
    @DisplayName("EVENT 단독 트리에서 stub이 EventConditionNotReadyException 던지면 SEGMENT_501_EVENT_NOT_READY")
    void eventOnlyTreeMappedTo501WhenStub() {
        GroupNode tree = eventOnlyTree();
        when(hasher.hash(eq(PROJECT_ID), any())).thenReturn(HASH);
        when(hllPreviewExecutor.getCached(HASH)).thenReturn(Optional.empty());
        when(eventAggReader.queryMatchingUserIds(any(EventNode.class), any(EventQueryContext.class)))
                .thenThrow(new EventConditionNotReadyException());

        assertThatThrownBy(() -> previewService.preview(PROJECT_ID, tree))
                .isInstanceOfSatisfying(
                        com.b101.butterfly.global.error.BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(
                                        com.b101.butterfly.segment.exception.SegmentErrorCode
                                                .SEGMENT_501_EVENT_NOT_READY));
    }

    @Test
    @DisplayName("mixed 트리: SQL 변환 → countUserMatches → exact 캐시 (S14P31B101-275)")
    void mixedTreeUsesSqlConverter() {
        GroupNode tree = mixedTree();
        when(hasher.hash(eq(PROJECT_ID), any())).thenReturn(HASH);
        when(hllPreviewExecutor.getCachedExact(HASH)).thenReturn(OptionalLong.empty());
        SqlFragment fragment = new SqlFragment("SELECT u.user_id FROM users u WHERE mixed", Map.of());
        when(sqlConverter.convert(eq(tree), any(EventQueryContext.class))).thenReturn(fragment);
        when(userDirectoryReader.countUserMatches(eq(fragment.sql()), eq(fragment.params())))
                .thenReturn(2L);

        PreviewResponse response = previewService.preview(PROJECT_ID, tree);

        assertThat(response.estimatedCount()).isEqualTo(2);
        verify(eventAggReader, never()).queryMatchingUserIds(any(), any());
        verify(sqlConverter).convert(eq(tree), any(EventQueryContext.class));
        verify(hllPreviewExecutor).cacheExactCount(eq(HASH), eq(2L), any());
    }

    @Test
    @DisplayName("빈 결과는 count 0 으로 반환 + exact 캐시에 sentinel (S14P31B101-275)")
    void emptyResultReturnsZero() {
        when(hasher.hash(eq(PROJECT_ID), any())).thenReturn(HASH);
        when(hllPreviewExecutor.getCachedExact(HASH)).thenReturn(OptionalLong.empty());
        when(sqlConverter.convert(any(), any(EventQueryContext.class)))
                .thenReturn(new SqlFragment("SELECT u.user_id FROM users u", Map.of()));
        when(userDirectoryReader.countUserMatches(anyString(), anyMap())).thenReturn(0L);

        PreviewResponse response = previewService.preview(PROJECT_ID, tagOnlyTree());

        assertThat(response.estimatedCount()).isZero();
        verify(hllPreviewExecutor).cacheExactCount(eq(HASH), eq(0L), any());
    }
}
