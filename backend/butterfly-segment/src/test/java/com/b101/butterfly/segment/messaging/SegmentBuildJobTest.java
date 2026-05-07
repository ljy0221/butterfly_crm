package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.port.EventAggReader;
import com.b101.butterfly.segment.port.EventQueryContext;
import com.b101.butterfly.segment.port.UserDirectoryReader;
import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.service.preview.ConditionTreeHasher;
import com.b101.butterfly.segment.service.preview.ConditionTreeToSqlConverter;
import com.b101.butterfly.segment.service.preview.SqlFragment;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SegmentBuildJob} 동작 검증 — PR3 완성형 빌드 로직.
 *
 * <p>EVENT-only / TAG·USER·DEVICE / mixed / 동시성 안전장치(b/c) / chunk 처리 검증.
 */
@ExtendWith(MockitoExtension.class)
class SegmentBuildJobTest {

    @Mock private SegmentRepository segmentRepository;
    @Mock private SegmentMemberRepository segmentMemberRepository;
    @Mock private ConditionTreeHasher conditionTreeHasher;
    @Mock private ConditionTreeToSqlConverter sqlConverter;
    @Mock private UserDirectoryReader userDirectoryReader;
    @Mock private EventAggReader eventAggReader;
    @Mock private com.b101.butterfly.segment.service.match.ActiveEventSegmentCache
            activeEventSegmentCache;

    @InjectMocks private SegmentBuildJob segmentBuildJob;

    private static final Long SEGMENT_ID = 42L;
    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 17L;
    private static final String HASH = "abcd1234";

    @Test
    @DisplayName("TAG-only 트리 — sqlConverter + userDirectoryReader 호출 + segment_members INSERT")
    void tagOnlyTree() {
        Segment segment = sampleSegment(tagTree());
        stubClaimAndHash(segment);
        SqlFragment fragment = new SqlFragment("SELECT u.user_id FROM users u WHERE ...", Map.of());
        when(sqlConverter.convert(any(), any(EventQueryContext.class))).thenReturn(fragment);
        when(userDirectoryReader.queryUserIds(any(), any()))
                .thenReturn(List.of(101L, 102L, 103L));
        when(segmentRepository.completeBuildIfOwner(eq(SEGMENT_ID), any(), anyInt(), any()))
                .thenReturn(1);

        segmentBuildJob.consume(message(HASH));

        verify(segmentMemberRepository).deleteBySegmentId(SEGMENT_ID);
        verify(userDirectoryReader).queryUserIds(any(), any());
        verify(eventAggReader, never()).queryMatchingUserIds(any(), any());
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(segmentRepository)
                .completeBuildIfOwner(eq(SEGMENT_ID), any(), countCaptor.capture(), any());
        assertThat(countCaptor.getValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("EVENT-only 트리 — materializeEventMatchesIntoSegment 단일 호출, saveAll 미호출")
    void eventOnlyTree() {
        Segment segment = sampleSegment(eventTree());
        stubClaimAndHash(segment);
        when(eventAggReader.materializeEventMatchesIntoSegment(eq(SEGMENT_ID), any(), any()))
                .thenReturn(2L);
        when(segmentRepository.completeBuildIfOwner(eq(SEGMENT_ID), any(), anyInt(), any()))
                .thenReturn(1);

        segmentBuildJob.consume(message(HASH));

        // S14P31B101-199 — filter 없는 EVENT 단독은 native INSERT-SELECT 단일 statement.
        verify(segmentMemberRepository).deleteBySegmentId(SEGMENT_ID);
        verify(eventAggReader).materializeEventMatchesIntoSegment(eq(SEGMENT_ID), any(), any());
        verify(eventAggReader, never()).queryMatchingUserIds(any(), any());
        verify(segmentMemberRepository, never()).saveAll(any());
        verify(sqlConverter, never()).convert(any(), any(EventQueryContext.class));
        verify(userDirectoryReader, never()).queryUserIds(any(), any());

        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(segmentRepository)
                .completeBuildIfOwner(eq(SEGMENT_ID), any(), countCaptor.capture(), any());
        assertThat(countCaptor.getValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("필터가 있는 EVENT-only 트리 — SQL 변환 + segment_members INSERT")
    void filteredEventOnlyTreeUsesSqlConverter() {
        Segment segment = sampleSegment(filteredEventTree());
        stubClaimAndHash(segment);
        SqlFragment fragment = new SqlFragment("SELECT u.user_id FROM users u WHERE filtered_event", Map.of());
        when(sqlConverter.convert(any(), any(EventQueryContext.class))).thenReturn(fragment);
        when(userDirectoryReader.queryUserIds(eq(fragment.sql()), eq(fragment.params())))
                .thenReturn(List.of(211L, 212L));
        when(segmentRepository.completeBuildIfOwner(eq(SEGMENT_ID), any(), anyInt(), any()))
                .thenReturn(1);

        segmentBuildJob.consume(message(HASH));

        verify(eventAggReader, never()).queryMatchingUserIds(any(), any());
        verify(userDirectoryReader).queryUserIds(eq(fragment.sql()), eq(fragment.params()));
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(segmentRepository)
                .completeBuildIfOwner(eq(SEGMENT_ID), any(), countCaptor.capture(), any());
        assertThat(countCaptor.getValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("mixed 트리 (EVENT + TAG) — SQL 변환 + segment_members INSERT")
    void mixedTreeUsesSqlConverter() {
        Segment segment = sampleSegment(mixedTree());
        stubClaimAndHash(segment);
        SqlFragment fragment = new SqlFragment("SELECT u.user_id FROM users u WHERE mixed", Map.of());
        when(sqlConverter.convert(any(), any(EventQueryContext.class))).thenReturn(fragment);
        when(userDirectoryReader.queryUserIds(eq(fragment.sql()), eq(fragment.params())))
                .thenReturn(List.of(301L, 302L));
        when(segmentRepository.completeBuildIfOwner(eq(SEGMENT_ID), any(), anyInt(), any()))
                .thenReturn(1);

        segmentBuildJob.consume(message(HASH));

        verify(eventAggReader, never()).queryMatchingUserIds(any(), any());
        verify(userDirectoryReader).queryUserIds(eq(fragment.sql()), eq(fragment.params()));
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(segmentRepository)
                .completeBuildIfOwner(eq(SEGMENT_ID), any(), countCaptor.capture(), any());
        assertThat(countCaptor.getValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("chunk 경계 처리 — 1500건 → saveAll 2번 (1000+500), heartbeat 1번")
    void chunkBoundary() {
        Segment segment = sampleSegment(tagTree());
        stubClaimAndHash(segment);
        when(sqlConverter.convert(any(), any(EventQueryContext.class)))
                .thenReturn(new SqlFragment("SELECT ...", Map.of()));
        // 1500건 user_id (CHUNK_SIZE=1000)
        List<Long> userIds = LongStream.range(1, 1501).boxed().toList();
        when(userDirectoryReader.queryUserIds(any(), any())).thenReturn(userIds);
        when(segmentRepository.completeBuildIfOwner(eq(SEGMENT_ID), any(), anyInt(), any()))
                .thenReturn(1);

        segmentBuildJob.consume(message(HASH));

        // saveAll 정확히 2번 (1000-건 chunk + 500-건 chunk).
        // 단, production은 buffer 참조를 재사용하므로 ArgumentCaptor로 chunk 크기 검증은 부정확
        // → 호출 횟수 + total count로 검증.
        verify(segmentMemberRepository, times(2)).saveAll(any());
        // 첫 chunk(1000건 도달) 후 heartbeat 갱신 1회 (마지막 500건은 chunk 미달이라 갱신 X)
        verify(segmentRepository, times(1)).updateHeartbeat(eq(SEGMENT_ID), any(), any());

        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(segmentRepository)
                .completeBuildIfOwner(eq(SEGMENT_ID), any(), countCaptor.capture(), any());
        assertThat(countCaptor.getValue()).isEqualTo(1500);
    }

    @Test
    @DisplayName("결과 0건 — saveAll 미호출 (빈 buffer), completeBuild count=0")
    void zeroResults() {
        Segment segment = sampleSegment(tagTree());
        stubClaimAndHash(segment);
        when(sqlConverter.convert(any(), any(EventQueryContext.class)))
                .thenReturn(new SqlFragment("SELECT ...", Map.of()));
        when(userDirectoryReader.queryUserIds(any(), any())).thenReturn(Collections.emptyList());
        when(segmentRepository.completeBuildIfOwner(eq(SEGMENT_ID), any(), anyInt(), any()))
                .thenReturn(1);

        segmentBuildJob.consume(message(HASH));

        verify(segmentMemberRepository).deleteBySegmentId(SEGMENT_ID);
        verify(segmentMemberRepository, never()).saveAll(any());
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(segmentRepository)
                .completeBuildIfOwner(eq(SEGMENT_ID), any(), countCaptor.capture(), any());
        assertThat(countCaptor.getValue()).isZero();
    }

    @Test
    @DisplayName("점유 실패 — 즉시 종료, deleteBySegmentId 등 후속 호출 없음")
    void claimFailedAcksAndExits() {
        when(segmentRepository.markBuildingIfPossible(eq(SEGMENT_ID), any(), any(), any()))
                .thenReturn(0);

        segmentBuildJob.consume(message(HASH));

        verify(segmentRepository, never()).findById(anyLong());
        verify(segmentMemberRepository, never()).deleteBySegmentId(anyLong());
        verify(segmentRepository, never()).completeBuildIfOwner(anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("점유 후 segment 미존재 — failBuildIfOwner")
    void segmentDeletedDuringBuild() {
        when(segmentRepository.markBuildingIfPossible(eq(SEGMENT_ID), any(), any(), any()))
                .thenReturn(1);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.empty());

        segmentBuildJob.consume(message(HASH));

        verify(segmentRepository).failBuildIfOwner(eq(SEGMENT_ID), any());
        verify(segmentMemberRepository, never()).deleteBySegmentId(anyLong());
    }

    @Test
    @DisplayName("conditionTreeHash 불일치 — failBuildIfOwner, 멤버 변경 없음")
    void conditionTreeHashMismatch() {
        Segment segment = sampleSegment(tagTree());
        when(segmentRepository.markBuildingIfPossible(eq(SEGMENT_ID), any(), any(), any()))
                .thenReturn(1);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(segment));
        when(conditionTreeHasher.hash(anyLong(), any())).thenReturn("different-hash");

        segmentBuildJob.consume(message(HASH));

        verify(segmentRepository).failBuildIfOwner(eq(SEGMENT_ID), any());
        verify(segmentMemberRepository, never()).deleteBySegmentId(anyLong());
    }

    private void stubClaimAndHash(Segment segment) {
        when(segmentRepository.markBuildingIfPossible(eq(SEGMENT_ID), any(), any(), any()))
                .thenReturn(1);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(segment));
        when(conditionTreeHasher.hash(anyLong(), any())).thenReturn(HASH);
    }

    private SegmentRebuildMessage message(String hash) {
        return new SegmentRebuildMessage(
                SEGMENT_ID, PROJECT_ID, MEMBER_ID, Instant.now(), hash);
    }

    private static GroupNode tagTree() {
        return new GroupNode(
                LogicOp.AND,
                List.of(
                        new TagNode(
                                "region",
                                Operator.EQ,
                                JsonNodeFactory.instance.textNode("서울"))));
    }

    private static GroupNode eventTree() {
        EventNode event =
                new EventNode(
                        42L, new ArrayList<EventFilter>(), new Period.Cumulative(), 1);
        return new GroupNode(LogicOp.AND, List.of(event));
    }

    private static GroupNode filteredEventTree() {
        EventNode event =
                new EventNode(
                        42L,
                        List.of(
                                new EventFilter(
                                        "productId",
                                        Operator.EQ,
                                        JsonNodeFactory.instance.textNode("SKU-1"))),
                        new Period.Cumulative(),
                        1);
        return new GroupNode(LogicOp.AND, List.of(event));
    }

    private static GroupNode mixedTree() {
        EventNode event =
                new EventNode(
                        42L, new ArrayList<EventFilter>(), new Period.Cumulative(), 1);
        TagNode tag =
                new TagNode("region", Operator.EQ, JsonNodeFactory.instance.textNode("서울"));
        return new GroupNode(LogicOp.AND, List.of(event, tag));
    }

    private Segment sampleSegment(GroupNode tree) {
        Segment segment =
                Segment.builder()
                        .projectId(PROJECT_ID)
                        .name("VIP")
                        .description("desc")
                        .conditionTree(tree)
                        .createdBy(MEMBER_ID)
                        .build();
        org.springframework.test.util.ReflectionTestUtils.setField(segment, "id", SEGMENT_ID);
        return segment;
    }
}
