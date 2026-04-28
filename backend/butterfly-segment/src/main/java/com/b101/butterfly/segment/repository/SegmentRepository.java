package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.Segment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Segment Repository. {@link Segment}는 {@code @SQLRestriction("is_deleted = false")}가 걸려 있어
 * 표준 메서드(findById, findAll 등)도 자동으로 soft-delete된 행을 제외한다.
 */
public interface SegmentRepository extends JpaRepository<Segment, Long> {

    Page<Segment> findByProjectId(Long projectId, Pageable pageable);

    boolean existsByProjectIdAndName(Long projectId, String name);
}
