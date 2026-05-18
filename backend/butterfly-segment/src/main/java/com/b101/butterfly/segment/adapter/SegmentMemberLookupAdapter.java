package com.b101.butterfly.segment.adapter;

import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.segment.port.SegmentAudienceTarget;
import com.b101.butterfly.segment.port.SegmentMemberLookupPort;
import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SegmentMemberLookupPort}의 segment-side 구현체.
 *
 * <p>Worker BC는 본 어댑터를 모르고 포트만 의존한다.
 * 페이징 없이 전체 활성 멤버를 조회 — 대용량 세그먼트는 추후 배치 처리로 분리.
 */
@Component
@RequiredArgsConstructor
public class SegmentMemberLookupAdapter implements SegmentMemberLookupPort {

    private final SegmentMemberRepository segmentMemberRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Long> getActiveUserIds(Long segmentId) {
        if (segmentId == null) {
            return Collections.emptyList();
        }

        // TODO: 대용량 세그먼트 처리 (페이징/스트리밍) — Phase 후속
        // 현재는 전체 조회 (메모리 부담 있음)
        return segmentMemberRepository
                .findActiveBySegmentId(segmentId, LocalDateTime.now(), PageRequest.ofSize(Integer.MAX_VALUE))
                .stream()
                .map(SegmentMember::getUserId)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SegmentAudienceTarget> getActiveTargets(
            Long segmentId, Long afterUserId, int limit) {
        if (segmentId == null || limit <= 0) {
            return Collections.emptyList();
        }

        Long cursor = afterUserId != null ? afterUserId : Long.MIN_VALUE;
        return segmentMemberRepository
                .findActiveBySegmentIdAfterUserId(
                        segmentId,
                        cursor,
                        LocalDateTime.now(),
                        PageRequest.ofSize(limit))
                .stream()
                .map(sm -> new SegmentAudienceTarget(sm.getUserId(), sm.getVariables()))
                .toList();
    }
}
