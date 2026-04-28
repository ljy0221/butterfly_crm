package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentFavorite;
import com.b101.butterfly.segment.entity.SegmentFavoriteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SegmentFavoriteRepository
        extends JpaRepository<SegmentFavorite, SegmentFavoriteId> {

    boolean existsBySegmentIdAndMemberId(Long segmentId, Long memberId);

    @Transactional
    void deleteBySegmentIdAndMemberId(Long segmentId, Long memberId);

    List<SegmentFavorite> findByMemberId(Long memberId);
}
