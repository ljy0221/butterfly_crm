package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.entity.SegmentFavorite;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.repository.SegmentFavoriteRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private static <T> T any(Class<T> clazz) {
        return org.mockito.ArgumentMatchers.any(clazz);
    }
}
