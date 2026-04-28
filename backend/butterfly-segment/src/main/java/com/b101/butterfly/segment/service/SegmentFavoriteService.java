package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.entity.SegmentFavorite;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.repository.SegmentFavoriteRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 즐겨찾기 등록/해제. 둘 다 멱등 — 이미 있는 상태로 add 호출하거나 없는 상태로 remove 호출해도 예외 없이 no-op.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SegmentFavoriteService {

    private final SegmentRepository segmentRepository;
    private final SegmentFavoriteRepository segmentFavoriteRepository;

    public void addFavorite(Long projectId, Long segmentId, Long memberId) {
        ensureSegmentExists(segmentId);
        if (segmentFavoriteRepository.existsBySegmentIdAndMemberId(segmentId, memberId)) {
            return;
        }
        segmentFavoriteRepository.save(
                SegmentFavorite.builder().segmentId(segmentId).memberId(memberId).build());
    }

    public void removeFavorite(Long projectId, Long segmentId, Long memberId) {
        ensureSegmentExists(segmentId);
        if (!segmentFavoriteRepository.existsBySegmentIdAndMemberId(segmentId, memberId)) {
            return;
        }
        segmentFavoriteRepository.deleteBySegmentIdAndMemberId(segmentId, memberId);
    }

    private void ensureSegmentExists(Long segmentId) {
        if (!segmentRepository.existsById(segmentId)) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_404_NOT_FOUND);
        }
    }
}
