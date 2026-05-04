package com.b101.butterfly.segment.service.match;

import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.port.EventAggReader;
import com.b101.butterfly.segment.port.EventQueryContext;
import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.service.preview.EventConditionNotReadyException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegmentEventMatchServiceTest {

    @Mock private SegmentRepository segmentRepository;
    @Mock private SegmentMemberRepository segmentMemberRepository;
    @Mock private EventAggReader eventAggReader;

    @InjectMocks private SegmentEventMatchService service;

    private static final Long PROJECT_ID = 1L;
    private static final Long SEGMENT_ID = 100L;
    private static final Long USER_ID = 42L;
    private static final Long CATEGORY_ID = 7L;
    private static final LocalDateTime MATCHED_AT = LocalDateTime.of(2026, 5, 4, 10, 0);

    @Test
    @DisplayName("매칭 시 UPSERT — Cumulative는 expires_at = NULL")
    void matchedCumulativeUpsertNullExpiry() {
        Segment seg = sampleSegment(eventOnly(new Period.Cumulative()));
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(eventAggReader.userMatches(any(), eq(USER_ID), any())).thenReturn(true);

        boolean result = service.evaluateAndUpsert(PROJECT_ID, SEGMENT_ID, USER_ID, MATCHED_AT);

        assertThat(result).isTrue();
        verify(segmentMemberRepository)
                .upsertMember(SEGMENT_ID, USER_ID, MATCHED_AT, null);
    }

    @Test
    @DisplayName("매칭 시 UPSERT — Relative(7일)은 matchedAt + 7일")
    void matchedRelativeExpiryIsMatchedPlusDays() {
        Segment seg = sampleSegment(eventOnly(new Period.Relative(7)));
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(eventAggReader.userMatches(any(), eq(USER_ID), any())).thenReturn(true);

        service.evaluateAndUpsert(PROJECT_ID, SEGMENT_ID, USER_ID, MATCHED_AT);

        verify(segmentMemberRepository)
                .upsertMember(SEGMENT_ID, USER_ID, MATCHED_AT, MATCHED_AT.plusDays(7));
    }

    @Test
    @DisplayName("매칭 시 UPSERT — Absolute는 endDate 다음날 00:00")
    void matchedAbsoluteExpiryIsEndPlusOneAtMidnight() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 30);
        Segment seg = sampleSegment(eventOnly(new Period.Absolute(start, end)));
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(eventAggReader.userMatches(any(), eq(USER_ID), any())).thenReturn(true);

        service.evaluateAndUpsert(PROJECT_ID, SEGMENT_ID, USER_ID, MATCHED_AT);

        LocalDateTime expectedExpiry = end.plusDays(1).atTime(LocalTime.MIDNIGHT);
        verify(segmentMemberRepository)
                .upsertMember(SEGMENT_ID, USER_ID, MATCHED_AT, expectedExpiry);
    }

    @Test
    @DisplayName("미매칭 시 UPSERT 미호출")
    void notMatchedDoesNotUpsert() {
        Segment seg = sampleSegment(eventOnly(new Period.Cumulative()));
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(eventAggReader.userMatches(any(), eq(USER_ID), any())).thenReturn(false);

        boolean result = service.evaluateAndUpsert(PROJECT_ID, SEGMENT_ID, USER_ID, MATCHED_AT);

        assertThat(result).isFalse();
        verify(segmentMemberRepository, never())
                .upsertMember(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("Segment 미존재 시 false + UPSERT 미호출 (캐시 stale)")
    void segmentNotFoundReturnsFalse() {
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.empty());

        boolean result = service.evaluateAndUpsert(PROJECT_ID, SEGMENT_ID, USER_ID, MATCHED_AT);

        assertThat(result).isFalse();
        verify(eventAggReader, never()).userMatches(any(), anyLong(), any());
        verify(segmentMemberRepository, never())
                .upsertMember(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("Mixed 트리 (캐시 stale 방어) → false + UPSERT 미호출")
    void mixedTreeReturnsFalse() {
        EventNode event = new EventNode(CATEGORY_ID, List.of(), new Period.Cumulative(), 1);
        TagNode tag = new TagNode("region", Operator.EQ, JsonNodeFactory.instance.textNode("서울"));
        GroupNode tree = new GroupNode(LogicOp.AND, List.of(event, tag));
        Segment seg = sampleSegment(tree);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));

        boolean result = service.evaluateAndUpsert(PROJECT_ID, SEGMENT_ID, USER_ID, MATCHED_AT);

        assertThat(result).isFalse();
        verify(eventAggReader, never()).userMatches(any(), anyLong(), any());
    }

    @Test
    @DisplayName("EventConditionNotReadyException — false 반환 (이벤트 모듈 미합류 방어)")
    void eventNotReadyReturnsFalse() {
        Segment seg = sampleSegment(eventOnly(new Period.Cumulative()));
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(eventAggReader.userMatches(any(), eq(USER_ID), any()))
                .thenThrow(new EventConditionNotReadyException());

        boolean result = service.evaluateAndUpsert(PROJECT_ID, SEGMENT_ID, USER_ID, MATCHED_AT);

        assertThat(result).isFalse();
        verify(segmentMemberRepository, never())
                .upsertMember(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("EventQueryContext가 projectId + Asia/Seoul 포함")
    void contextHasProjectAndSeoul() {
        Segment seg = sampleSegment(eventOnly(new Period.Cumulative()));
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(eventAggReader.userMatches(any(), eq(USER_ID), any())).thenReturn(false);

        service.evaluateAndUpsert(PROJECT_ID, SEGMENT_ID, USER_ID, MATCHED_AT);

        ArgumentCaptor<EventQueryContext> ctxCaptor =
                ArgumentCaptor.forClass(EventQueryContext.class);
        verify(eventAggReader).userMatches(any(), eq(USER_ID), ctxCaptor.capture());
        EventQueryContext ctx = ctxCaptor.getValue();
        assertThat(ctx.projectId()).isEqualTo(PROJECT_ID);
        assertThat(ctx.timeZone().getId()).isEqualTo("Asia/Seoul");
        assertThat(ctx.asOfDate()).isNotNull();
    }

    private static GroupNode eventOnly(Period period) {
        EventNode event = new EventNode(CATEGORY_ID, List.of(), period, 1);
        return new GroupNode(LogicOp.AND, List.of(event));
    }

    private static Segment sampleSegment(GroupNode tree) {
        Segment s = Segment.builder()
                .projectId(PROJECT_ID)
                .name("test")
                .description("d")
                .conditionTree(tree)
                .createdBy(1L)
                .build();
        ReflectionTestUtils.setField(s, "id", SEGMENT_ID);
        ReflectionTestUtils.setField(s, "buildStatus", BuildStatus.READY);
        return s;
    }
}
