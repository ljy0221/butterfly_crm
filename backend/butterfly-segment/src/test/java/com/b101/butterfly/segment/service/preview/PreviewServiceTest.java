package com.b101.butterfly.segment.service.preview;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.validation.ConditionTreeValidator;
import com.b101.butterfly.segment.dto.response.PreviewResponse;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.port.UserDirectoryReader;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreviewServiceTest {

    @Mock private ConditionTreeValidator conditionTreeValidator;
    @Mock private ConditionTreeToSqlConverter sqlConverter;
    @Mock private UserDirectoryReader userDirectoryReader;
    @Mock private HllPreviewExecutor hllPreviewExecutor;
    @Mock private ConditionTreeHasher hasher;

    @InjectMocks private PreviewService previewService;

    private static final Long PROJECT_ID = 1L;
    private static final String HASH = "abc123";
    private static GroupNode tree;

    @BeforeEach
    void setUp() {
        tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new TagNode(
                                        "region",
                                        Operator.EQ,
                                        JsonNodeFactory.instance.textNode("서울"))));
    }

    @Test
    @DisplayName("캐시 hit 시 cached count 반환 + 변환기/reader 미호출")
    void cacheHitSkipsComputation() {
        when(hasher.hash(eq(PROJECT_ID), any())).thenReturn(HASH);
        when(hllPreviewExecutor.getCachedCount(HASH)).thenReturn(OptionalLong.of(1820L));

        PreviewResponse response = previewService.preview(PROJECT_ID, tree);

        assertThat(response.estimatedCount()).isEqualTo(1820);
        assertThat(response.method()).isEqualTo("HLL");
        verify(sqlConverter, never()).convert(any(), anyLong());
        verify(userDirectoryReader, never()).queryUserIds(anyString(), anyMap());
        verify(hllPreviewExecutor, never()).computeAndCache(anyString(), anyList(), any());
    }

    @Test
    @DisplayName("캐시 miss 시 SQL 변환 → reader 조회 → HLL 캐시 → count 반환")
    void cacheMissComputesAndCaches() {
        when(hasher.hash(eq(PROJECT_ID), any())).thenReturn(HASH);
        when(hllPreviewExecutor.getCachedCount(HASH)).thenReturn(OptionalLong.empty());
        SqlFragment fragment = new SqlFragment("SELECT u.user_id FROM users u", Map.of());
        when(sqlConverter.convert(any(), eq(PROJECT_ID))).thenReturn(fragment);
        when(userDirectoryReader.queryUserIds(eq(fragment.sql()), eq(fragment.params())))
                .thenReturn(List.of(1L, 2L, 3L));
        when(hllPreviewExecutor.computeAndCache(eq(HASH), eq(List.of(1L, 2L, 3L)), any()))
                .thenReturn(3L);

        PreviewResponse response = previewService.preview(PROJECT_ID, tree);

        assertThat(response.estimatedCount()).isEqualTo(3);
        verify(conditionTreeValidator).validate(tree);
    }

    @Test
    @DisplayName("EVENT 노드 포함 시 SEGMENT_501_EVENT_NOT_READY로 변환 + reader 미호출")
    void eventNodeMappedTo501() {
        when(hasher.hash(eq(PROJECT_ID), any())).thenReturn(HASH);
        when(hllPreviewExecutor.getCachedCount(HASH)).thenReturn(OptionalLong.empty());
        when(sqlConverter.convert(any(), eq(PROJECT_ID)))
                .thenThrow(new EventConditionNotReadyException());

        assertThatThrownBy(() -> previewService.preview(PROJECT_ID, tree))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_501_EVENT_NOT_READY));

        verify(userDirectoryReader, never()).queryUserIds(anyString(), anyMap());
        verify(hllPreviewExecutor, never()).computeAndCache(anyString(), anyList(), any());
    }

    @Test
    @DisplayName("빈 user 결과는 count 0으로 반환된다 (HllExecutor가 sentinel 처리)")
    void emptyResultReturnsZero() {
        when(hasher.hash(eq(PROJECT_ID), any())).thenReturn(HASH);
        when(hllPreviewExecutor.getCachedCount(HASH)).thenReturn(OptionalLong.empty());
        when(sqlConverter.convert(any(), eq(PROJECT_ID)))
                .thenReturn(new SqlFragment("SELECT u.user_id FROM users u", Map.of()));
        when(userDirectoryReader.queryUserIds(anyString(), anyMap())).thenReturn(List.of());
        when(hllPreviewExecutor.computeAndCache(eq(HASH), eq(List.of()), any())).thenReturn(0L);

        PreviewResponse response = previewService.preview(PROJECT_ID, tree);

        assertThat(response.estimatedCount()).isZero();
    }
}
