package com.b101.butterfly.segment.adapter.in;

import com.b101.butterfly.segment.api.SegmentLookupPort;
import com.b101.butterfly.segment.api.SegmentSnapshot;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link SegmentLookupPort}의 inbound 어댑터. 외부 모듈(campaign 등)이 segment 정보를 ID로 조회할 때
 * 본 어댑터가 {@code SegmentRepository}로 위임 후 {@link SegmentSnapshot}으로 변환해 반환한다.
 *
 * <p>{@code Segment} 엔티티는 {@code @SQLRestriction("is_deleted = false")}로 soft-deleted 행을
 * 자동 제외한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SegmentLookupAdapter implements SegmentLookupPort {

    private final SegmentRepository segmentRepository;

    @Override
    public Optional<SegmentSnapshot> findById(Long segmentId) {
        return segmentRepository.findById(segmentId).map(SegmentLookupAdapter::toSnapshot);
    }

    @Override
    public List<SegmentSnapshot> findAllByIds(Collection<Long> segmentIds) {
        if (segmentIds == null || segmentIds.isEmpty()) {
            return List.of();
        }
        return segmentRepository.findAllById(segmentIds).stream()
                .map(SegmentLookupAdapter::toSnapshot)
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
