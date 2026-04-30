package com.b101.butterfly.segment.api;

import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link SegmentLookupPort} 구현체. {@code SegmentRepository}로 단건/다건 조회 후
 * {@link SegmentSnapshot}으로 변환한다.
 *
 * <p>{@code Segment}는 {@code @SQLRestriction("is_deleted = false")}로 soft-deleted 행을 자동 제외한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SegmentLookupPortImpl implements SegmentLookupPort {

    private final SegmentRepository segmentRepository;

    @Override
    public Optional<SegmentSnapshot> findById(Long segmentId) {
        return segmentRepository.findById(segmentId).map(SegmentLookupPortImpl::toSnapshot);
    }

    @Override
    public List<SegmentSnapshot> findAllByIds(Collection<Long> segmentIds) {
        if (segmentIds == null || segmentIds.isEmpty()) {
            return List.of();
        }
        return segmentRepository.findAllById(segmentIds).stream()
                .map(SegmentLookupPortImpl::toSnapshot)
                .toList();
    }

    private static SegmentSnapshot toSnapshot(Segment segment) {
        return new SegmentSnapshot(
                segment.getId(),
                segment.getProjectId(),
                segment.getName(),
                segment.getDescription(),
                segment.getBuildStatus(),
                segment.getEstimatedCount(),
                segment.getEstimatedAt(),
                segment.getLastBuiltAt(),
                segment.getCreatedBy(),
                segment.getCreatedAt(),
                segment.getUpdatedAt());
    }
}
