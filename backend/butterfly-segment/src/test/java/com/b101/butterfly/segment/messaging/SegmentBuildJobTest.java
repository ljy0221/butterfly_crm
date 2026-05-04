package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.service.preview.ConditionTreeHasher;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SegmentBuildJob} PR2 minimal 동작 검증.
 *
 * <p>실제 매칭 user 조회·segment_members INSERT는 PR3에서 추가되므로 본 테스트는 흐름·CAS 호출
 * 패턴·conditionTreeHash 비교만 검증.
 */
@ExtendWith(MockitoExtension.class)
class SegmentBuildJobTest {

    @Mock private SegmentRepository segmentRepository;
    @Mock private ConditionTreeHasher conditionTreeHasher;

    @InjectMocks private SegmentBuildJob segmentBuildJob;

    private static final Long SEGMENT_ID = 42L;
    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 17L;
    private static final String HASH = "abcd1234";

    @Test
    @DisplayName("정상 흐름 — markBuildingIfPossible 점유 + hash 일치 + completeBuildIfOwner 호출")
    void happyPath() {
        when(segmentRepository.markBuildingIfPossible(
                        eq(SEGMENT_ID), any(), any(), any()))
                .thenReturn(1);
        Segment segment = sampleSegment();
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(segment));
        when(conditionTreeHasher.hash(anyLong(), any())).thenReturn(HASH);
        when(segmentRepository.completeBuildIfOwner(
                        eq(SEGMENT_ID), any(), anyInt(), any()))
                .thenReturn(1);

        segmentBuildJob.consume(message(HASH));

        verify(segmentRepository)
                .markBuildingIfPossible(eq(SEGMENT_ID), any(), any(), any());
        // PR2는 placeholder estimatedCount=0
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(segmentRepository)
                .completeBuildIfOwner(eq(SEGMENT_ID), any(), countCaptor.capture(), any());
        assertThat(countCaptor.getValue()).isZero();
        verify(segmentRepository, never()).failBuildIfOwner(anyLong(), any());
    }

    @Test
    @DisplayName("점유 실패 — 다른 worker가 신선하게 처리 중이면 즉시 종료")
    void claimFailedAcksAndExits() {
        when(segmentRepository.markBuildingIfPossible(
                        eq(SEGMENT_ID), any(), any(), any()))
                .thenReturn(0);

        segmentBuildJob.consume(message(HASH));

        verify(segmentRepository, never()).findById(anyLong());
        verify(segmentRepository, never()).completeBuildIfOwner(anyLong(), any(), anyInt(), any());
        verify(segmentRepository, never()).failBuildIfOwner(anyLong(), any());
    }

    @Test
    @DisplayName("점유 후 segment 미존재 — 빌드 중 삭제로 간주, failBuildIfOwner 호출")
    void segmentDeletedDuringBuild() {
        when(segmentRepository.markBuildingIfPossible(
                        eq(SEGMENT_ID), any(), any(), any()))
                .thenReturn(1);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.empty());

        segmentBuildJob.consume(message(HASH));

        verify(segmentRepository).failBuildIfOwner(eq(SEGMENT_ID), any());
        verify(segmentRepository, never())
                .completeBuildIfOwner(anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("conditionTreeHash 불일치 — 빌드 중 tree 수정으로 간주, failBuildIfOwner")
    void conditionTreeHashMismatch() {
        when(segmentRepository.markBuildingIfPossible(
                        eq(SEGMENT_ID), any(), any(), any()))
                .thenReturn(1);
        Segment segment = sampleSegment();
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(segment));
        // 메시지의 hash는 HASH지만 현재 tree는 다른 hash
        when(conditionTreeHasher.hash(anyLong(), any())).thenReturn("different-hash");

        segmentBuildJob.consume(message(HASH));

        verify(segmentRepository).failBuildIfOwner(eq(SEGMENT_ID), any());
        verify(segmentRepository, never())
                .completeBuildIfOwner(anyLong(), any(), anyInt(), any());
    }

    private SegmentRebuildMessage message(String hash) {
        return new SegmentRebuildMessage(
                SEGMENT_ID, PROJECT_ID, MEMBER_ID, Instant.now(), hash);
    }

    private Segment sampleSegment() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new TagNode(
                                        "region",
                                        Operator.EQ,
                                        JsonNodeFactory.instance.textNode("서울"))));
        return Segment.builder()
                .projectId(PROJECT_ID)
                .name("VIP")
                .description("desc")
                .conditionTree(tree)
                .createdBy(MEMBER_ID)
                .build();
    }
}
