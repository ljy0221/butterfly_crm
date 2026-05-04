package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.dto.response.SegmentUserResponse;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.port.UserSummary;
import com.b101.butterfly.segment.port.UserSummaryReader;
import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegmentUsersServiceTest {

    @Mock private SegmentRepository segmentRepository;
    @Mock private SegmentMemberRepository segmentMemberRepository;
    @Mock private UserSummaryReader userSummaryReader;

    @InjectMocks private SegmentUsersService segmentUsersService;

    private static final Long PROJECT_ID = 1L;
    private static final Long SEGMENT_ID = 42L;
    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Test
    @DisplayName("정상 — segment_members 페이지 + UserSummary 매핑")
    void happyPath() {
        Segment seg = sampleSegment(PROJECT_ID);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));

        SegmentMember m1 = member(SEGMENT_ID, 100L, LocalDateTime.of(2026, 5, 1, 10, 0));
        SegmentMember m2 = member(SEGMENT_ID, 200L, LocalDateTime.of(2026, 5, 2, 11, 0));
        Page<SegmentMember> page = new PageImpl<>(List.of(m1, m2), PAGE, 2);
        when(segmentMemberRepository.findActiveBySegmentId(eq(SEGMENT_ID), any(), eq(PAGE)))
                .thenReturn(page);

        when(userSummaryReader.findByUserIds(eq(PROJECT_ID), eq(List.of(100L, 200L))))
                .thenReturn(
                        Map.of(
                                100L, new UserSummary(100L, "ext-100", "a@x.com", null),
                                200L, new UserSummary(200L, "ext-200", "b@x.com", null)));

        PageResponse<SegmentUserResponse> response =
                segmentUsersService.listUsers(PROJECT_ID, SEGMENT_ID, PAGE);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content())
                .extracting(SegmentUserResponse::userId, SegmentUserResponse::externalId, SegmentUserResponse::email)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(100L, "ext-100", "a@x.com"),
                        org.assertj.core.groups.Tuple.tuple(200L, "ext-200", "b@x.com"));
        assertThat(response.content())
                .allSatisfy(u -> assertThat(u.lastActiveAt()).isNull());
    }

    @Test
    @DisplayName("미존재 segment — SEGMENT_404_NOT_FOUND")
    void segmentNotFound() {
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> segmentUsersService.listUsers(PROJECT_ID, SEGMENT_ID, PAGE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_404_NOT_FOUND));

        verify(segmentMemberRepository, never())
                .findActiveBySegmentId(anyLong(), any(), any());
    }

    @Test
    @DisplayName("다른 프로젝트의 segment — SEGMENT_404_NOT_FOUND (정보 노출 회피)")
    void wrongProjectReturns404() {
        Segment seg = sampleSegment(99L); // 다른 projectId
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));

        assertThatThrownBy(() -> segmentUsersService.listUsers(PROJECT_ID, SEGMENT_ID, PAGE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_404_NOT_FOUND));

        verify(segmentMemberRepository, never())
                .findActiveBySegmentId(anyLong(), any(), any());
    }

    @Test
    @DisplayName("빈 페이지 — UserSummaryReader는 빈 리스트로 호출")
    void emptyPage() {
        Segment seg = sampleSegment(PROJECT_ID);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(segmentMemberRepository.findActiveBySegmentId(eq(SEGMENT_ID), any(), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(), PAGE, 0));
        when(userSummaryReader.findByUserIds(eq(PROJECT_ID), eq(List.of())))
                .thenReturn(Map.of());

        PageResponse<SegmentUserResponse> response =
                segmentUsersService.listUsers(PROJECT_ID, SEGMENT_ID, PAGE);

        assertThat(response.totalElements()).isZero();
        assertThat(response.content()).isEmpty();
    }

    @Test
    @DisplayName("일부 user 미존재 — externalId/email은 null로 채우고 userId/matchedAt은 유지")
    void partialUserMissing() {
        Segment seg = sampleSegment(PROJECT_ID);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));

        SegmentMember m1 = member(SEGMENT_ID, 100L, LocalDateTime.of(2026, 5, 1, 10, 0));
        SegmentMember m2 = member(SEGMENT_ID, 200L, LocalDateTime.of(2026, 5, 2, 11, 0));
        when(segmentMemberRepository.findActiveBySegmentId(eq(SEGMENT_ID), any(), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(m1, m2), PAGE, 2));

        // 100L만 존재, 200L 미존재 (placeholder/타프로젝트)
        when(userSummaryReader.findByUserIds(eq(PROJECT_ID), eq(List.of(100L, 200L))))
                .thenReturn(Map.of(100L, new UserSummary(100L, "ext-100", "a@x.com", null)));

        PageResponse<SegmentUserResponse> response =
                segmentUsersService.listUsers(PROJECT_ID, SEGMENT_ID, PAGE);

        assertThat(response.content()).hasSize(2);
        SegmentUserResponse first = response.content().get(0);
        assertThat(first.userId()).isEqualTo(100L);
        assertThat(first.externalId()).isEqualTo("ext-100");
        SegmentUserResponse second = response.content().get(1);
        assertThat(second.userId()).isEqualTo(200L);
        assertThat(second.externalId()).isNull();
        assertThat(second.email()).isNull();
        assertThat(second.matchedAt()).isNotNull();
    }

    @Test
    @DisplayName("findActiveBySegmentId — now 인자가 호출 시점에 가까워야 함 (만료 필터)")
    void activeFilterUsesCurrentTime() {
        Segment seg = sampleSegment(PROJECT_ID);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(segmentMemberRepository.findActiveBySegmentId(eq(SEGMENT_ID), any(), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(), PAGE, 0));
        when(userSummaryReader.findByUserIds(any(), any())).thenReturn(Map.of());

        LocalDateTime before = LocalDateTime.now();
        segmentUsersService.listUsers(PROJECT_ID, SEGMENT_ID, PAGE);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(segmentMemberRepository)
                .findActiveBySegmentId(eq(SEGMENT_ID), nowCaptor.capture(), eq(PAGE));
        assertThat(nowCaptor.getValue()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    private static Segment sampleSegment(Long projectId) {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new TagNode(
                                        "region",
                                        Operator.EQ,
                                        JsonNodeFactory.instance.textNode("서울"))));
        Segment seg =
                Segment.builder()
                        .projectId(projectId)
                        .name("VIP")
                        .description("desc")
                        .conditionTree(tree)
                        .createdBy(1L)
                        .build();
        ReflectionTestUtils.setField(seg, "id", SEGMENT_ID);
        return seg;
    }

    private static SegmentMember member(Long segmentId, Long userId, LocalDateTime matchedAt) {
        SegmentMember sm = SegmentMember.builder().segmentId(segmentId).userId(userId).build();
        ReflectionTestUtils.setField(sm, "matchedAt", matchedAt);
        return sm;
    }
}
