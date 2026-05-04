package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.segment.entity.SegmentMemberId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SegmentMemberRepository extends JpaRepository<SegmentMember, SegmentMemberId> {

    long countBySegmentId(Long segmentId);

    Page<SegmentMember> findBySegmentId(Long segmentId, Pageable pageable);

    /**
     * rebuild 시점에 기존 멤버 일괄 삭제. 새 매칭으로 INSERT 전 호출.
     *
     * @return 삭제된 row 수 (estimated 기준 비교용)
     */
    @Modifying
    @Query("DELETE FROM SegmentMember sm WHERE sm.segmentId = :segmentId")
    int deleteBySegmentId(@Param("segmentId") Long segmentId);
}
