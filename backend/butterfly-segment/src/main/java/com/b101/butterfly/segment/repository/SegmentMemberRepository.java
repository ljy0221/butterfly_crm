package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.segment.entity.SegmentMemberId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SegmentMemberRepository extends JpaRepository<SegmentMember, SegmentMemberId> {

    long countBySegmentId(Long segmentId);

    Page<SegmentMember> findBySegmentId(Long segmentId, Pageable pageable);
}
