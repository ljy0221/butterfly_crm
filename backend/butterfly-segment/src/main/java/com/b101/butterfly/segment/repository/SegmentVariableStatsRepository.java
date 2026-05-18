package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentVariableStats;
import com.b101.butterfly.segment.entity.SegmentVariableStatsId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SegmentVariableStatsRepository
        extends JpaRepository<SegmentVariableStats, SegmentVariableStatsId> {

    List<SegmentVariableStats> findByProjectIdAndSegmentIdOrderByVariableKeyAsc(
            Long projectId, Long segmentId);
}
