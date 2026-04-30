package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.dto.response.FavoritesResponse;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.entity.SegmentFavorite;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.repository.SegmentFavoriteRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegmentFavoriteServiceTest {

    @Mock private SegmentRepository segmentRepository;
    @Mock private SegmentFavoriteRepository segmentFavoriteRepository;

    @InjectMocks private SegmentFavoriteService segmentFavoriteService;

    private static final Long PROJECT_ID = 1L;
    private static final Long SEGMENT_ID = 42L;
    private static final Long MEMBER_ID = 17L;

    @Test
    @DisplayName("addFavorite — 미등록 상태에서 새로 등록한다")
    void addFavoriteCreatesNew() {
        when(segmentRepository.existsById(SEGMENT_ID)).thenReturn(true);
        when(segmentFavoriteRepository.existsBySegmentIdAndMemberId(SEGMENT_ID, MEMBER_ID))
                .thenReturn(false);

        segmentFavoriteService.addFavorite(PROJECT_ID, SEGMENT_ID, MEMBER_ID);

        verify(segmentFavoriteRepository, times(1)).save(any(SegmentFavorite.class));
    }

    @Test
    @DisplayName("addFavorite — 이미 등록된 상태면 멱등 (no-op)")
    void addFavoriteIdempotentWhenExists() {
        when(segmentRepository.existsById(SEGMENT_ID)).thenReturn(true);
        when(segmentFavoriteRepository.existsBySegmentIdAndMemberId(SEGMENT_ID, MEMBER_ID))
                .thenReturn(true);

        segmentFavoriteService.addFavorite(PROJECT_ID, SEGMENT_ID, MEMBER_ID);

        verify(segmentFavoriteRepository, never()).save(any(SegmentFavorite.class));
    }

    @Test
    @DisplayName("addFavorite — segment 미존재 시 SEGMENT_404_NOT_FOUND")
    void addFavoriteSegmentNotFound() {
        when(segmentRepository.existsById(SEGMENT_ID)).thenReturn(false);

        assertThatThrownBy(
                        () -> segmentFavoriteService.addFavorite(PROJECT_ID, SEGMENT_ID, MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_404_NOT_FOUND));
    }

    @Test
    @DisplayName("removeFavorite — 등록된 상태에서 삭제한다")
    void removeFavoriteDeletesWhenExists() {
        when(segmentRepository.existsById(SEGMENT_ID)).thenReturn(true);
        when(segmentFavoriteRepository.existsBySegmentIdAndMemberId(SEGMENT_ID, MEMBER_ID))
                .thenReturn(true);

        segmentFavoriteService.removeFavorite(PROJECT_ID, SEGMENT_ID, MEMBER_ID);

        verify(segmentFavoriteRepository, times(1))
                .deleteBySegmentIdAndMemberId(SEGMENT_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("removeFavorite — 미등록 상태면 멱등 (no-op)")
    void removeFavoriteIdempotentWhenAbsent() {
        when(segmentRepository.existsById(SEGMENT_ID)).thenReturn(true);
        when(segmentFavoriteRepository.existsBySegmentIdAndMemberId(SEGMENT_ID, MEMBER_ID))
                .thenReturn(false);

        segmentFavoriteService.removeFavorite(PROJECT_ID, SEGMENT_ID, MEMBER_ID);

        verify(segmentFavoriteRepository, never())
                .deleteBySegmentIdAndMemberId(SEGMENT_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("removeFavorite — segment 미존재 시 SEGMENT_404_NOT_FOUND")
    void removeFavoriteSegmentNotFound() {
        when(segmentRepository.existsById(SEGMENT_ID)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                segmentFavoriteService.removeFavorite(
                                        PROJECT_ID, SEGMENT_ID, MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_404_NOT_FOUND));
    }

    @Test
    @DisplayName("list — 즐겨찾기 없으면 빈 응답 반환 + segmentRepository 미호출")
    void listEmpty() {
        when(segmentFavoriteRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of());

        FavoritesResponse response = segmentFavoriteService.list(PROJECT_ID, MEMBER_ID);

        assertThat(response.favorites()).isEmpty();
        assertThat(response.recentlyUsed()).isEmpty();
        verify(segmentRepository, never()).findAllById(anyIterable());
    }

    @Test
    @DisplayName("list — 정상 조회는 favoritedAt 내림차순 + recentlyUsed는 빈 리스트")
    void listHappyPath() {
        LocalDateTime older = LocalDateTime.of(2026, 4, 25, 10, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 4, 28, 10, 0);
        SegmentFavorite favOlder = favoriteWithCreatedAt(42L, MEMBER_ID, older);
        SegmentFavorite favNewer = favoriteWithCreatedAt(99L, MEMBER_ID, newer);
        when(segmentFavoriteRepository.findByMemberId(MEMBER_ID))
                .thenReturn(List.of(favOlder, favNewer));

        Segment seg42 = sampleSegment(42L, "VIP", PROJECT_ID, 1820);
        Segment seg99 = sampleSegment(99L, "휴면 30일", PROJECT_ID, 12400);
        when(segmentRepository.findAllById(anyIterable())).thenReturn(List.of(seg42, seg99));

        FavoritesResponse response = segmentFavoriteService.list(PROJECT_ID, MEMBER_ID);

        assertThat(response.favorites())
                .extracting(FavoritesResponse.FavoriteItem::segmentId)
                .containsExactly(99L, 42L); // newer 먼저
        assertThat(response.favorites().get(0).favoritedAt()).isEqualTo(newer);
        assertThat(response.favorites().get(0).name()).isEqualTo("휴면 30일");
        assertThat(response.favorites().get(0).estimatedCount()).isEqualTo(12400);
        assertThat(response.recentlyUsed()).isEmpty();
    }

    @Test
    @DisplayName("list — 다른 프로젝트의 즐겨찾기는 제외한다")
    void listExcludesOtherProjects() {
        Long otherProject = 2L;
        SegmentFavorite favMine =
                favoriteWithCreatedAt(42L, MEMBER_ID, LocalDateTime.of(2026, 4, 28, 10, 0));
        SegmentFavorite favOther =
                favoriteWithCreatedAt(77L, MEMBER_ID, LocalDateTime.of(2026, 4, 27, 10, 0));
        when(segmentFavoriteRepository.findByMemberId(MEMBER_ID))
                .thenReturn(List.of(favMine, favOther));

        Segment segMine = sampleSegment(42L, "VIP", PROJECT_ID, 100);
        Segment segOther = sampleSegment(77L, "다른프로젝트", otherProject, 999);
        when(segmentRepository.findAllById(anyIterable()))
                .thenReturn(List.of(segMine, segOther));

        FavoritesResponse response = segmentFavoriteService.list(PROJECT_ID, MEMBER_ID);

        assertThat(response.favorites())
                .extracting(FavoritesResponse.FavoriteItem::segmentId)
                .containsExactly(42L);
    }

    private static <T> T any(Class<T> clazz) {
        return org.mockito.ArgumentMatchers.any(clazz);
    }

    private static SegmentFavorite favoriteWithCreatedAt(
            Long segmentId, Long memberId, LocalDateTime createdAt) {
        SegmentFavorite favorite =
                SegmentFavorite.builder().segmentId(segmentId).memberId(memberId).build();
        ReflectionTestUtils.setField(favorite, "createdAt", createdAt);
        return favorite;
    }

    private static Segment sampleSegment(
            Long id, String name, Long projectId, Integer estimatedCount) {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new TagNode(
                                        "region",
                                        Operator.EQ,
                                        JsonNodeFactory.instance.textNode("서울"))));
        Segment segment =
                Segment.builder()
                        .projectId(projectId)
                        .name(name)
                        .description("desc")
                        .conditionTree(tree)
                        .createdBy(MEMBER_ID)
                        .build();
        ReflectionTestUtils.setField(segment, "id", id);
        ReflectionTestUtils.setField(segment, "estimatedCount", estimatedCount);
        return segment;
    }
}
