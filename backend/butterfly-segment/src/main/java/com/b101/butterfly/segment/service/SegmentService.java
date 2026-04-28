package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.segment.domain.condition.validation.ConditionTreeValidator;
import com.b101.butterfly.segment.dto.request.CreateSegmentRequest;
import com.b101.butterfly.segment.dto.request.UpdateSegmentRequest;
import com.b101.butterfly.segment.dto.response.CreateSegmentResponse;
import com.b101.butterfly.segment.dto.response.CreatorSummary;
import com.b101.butterfly.segment.dto.response.LinkedCampaignSummary;
import com.b101.butterfly.segment.dto.response.SegmentDetailResponse;
import com.b101.butterfly.segment.dto.response.SegmentSummaryResponse;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.port.LinkedCampaignChecker;
import com.b101.butterfly.segment.port.LinkedCampaignsView;
import com.b101.butterfly.segment.repository.SegmentFavoriteRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 세그먼트 CRUD. Phase 4 핵심 — 컨트롤러는 본 서비스 호출로 더미 응답을 교체한다.
 *
 * <p>linkedCampaign 정보는 {@link LinkedCampaignChecker} 포트에 위임 — campaign 모듈 미합류 시
 * stub이 항상 false/empty를 반환하므로 수정/삭제 거절 케이스는 Phase 7에서 진짜 데이터로 검증된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SegmentService {

    private final SegmentRepository segmentRepository;
    private final SegmentFavoriteRepository segmentFavoriteRepository;
    private final ConditionTreeValidator conditionTreeValidator;
    private final LinkedCampaignChecker linkedCampaignChecker;

    @Transactional
    public CreateSegmentResponse create(
            Long projectId, Long createdBy, CreateSegmentRequest request) {
        if (segmentRepository.existsByProjectIdAndName(projectId, request.name())) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_400_NAME_DUPLICATED);
        }
        conditionTreeValidator.validate(request.conditionTree());

        Segment saved =
                segmentRepository.save(
                        Segment.builder()
                                .projectId(projectId)
                                .name(request.name())
                                .description(request.description())
                                .conditionTree(request.conditionTree())
                                .createdBy(createdBy)
                                .build());

        return new CreateSegmentResponse(
                saved.getId(),
                saved.getName(),
                saved.getDescription(),
                saved.getBuildStatus(),
                saved.getEstimatedCount(),
                saved.getEstimatedAt(),
                saved.getLastBuiltAt(),
                saved.getSchemaVersion(),
                saved.getCreatedBy(),
                saved.getCreatedAt());
    }

    public PageResponse<SegmentSummaryResponse> list(
            Long projectId, Long memberId, Pageable pageable) {
        Page<Segment> page = segmentRepository.findByProjectId(projectId, pageable);
        List<SegmentSummaryResponse> content =
                page.getContent().stream().map(s -> toSummary(s, memberId)).toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public SegmentDetailResponse detail(Long projectId, Long segmentId, Long memberId) {
        Segment segment = findActiveSegment(segmentId);
        return toDetail(segment, memberId);
    }

    @Transactional
    public SegmentDetailResponse update(
            Long projectId, Long segmentId, Long memberId, UpdateSegmentRequest request) {
        Segment segment = findActiveSegment(segmentId);

        if (linkedCampaignChecker.hasActiveOrSending(segmentId)) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_409_LINKED_ACTIVE_CAMPAIGN);
        }

        if (!segment.getName().equals(request.name())
                && segmentRepository.existsByProjectIdAndName(projectId, request.name())) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_400_NAME_DUPLICATED);
        }

        conditionTreeValidator.validate(request.conditionTree());

        segment.rename(request.name(), request.description());
        segment.replaceConditionTree(request.conditionTree());

        return toDetail(segment, memberId);
    }

    @Transactional
    public void delete(Long projectId, Long segmentId) {
        Segment segment = findActiveSegment(segmentId);
        if (linkedCampaignChecker.hasActiveOrSending(segmentId)) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_409_LINKED_ACTIVE_CAMPAIGN);
        }
        segment.softDelete();
    }

    private Segment findActiveSegment(Long segmentId) {
        return segmentRepository
                .findById(segmentId)
                .orElseThrow(() -> new BusinessException(SegmentErrorCode.SEGMENT_404_NOT_FOUND));
    }

    private SegmentSummaryResponse toSummary(Segment segment, Long memberId) {
        boolean favorited =
                memberId != null
                        && segmentFavoriteRepository.existsBySegmentIdAndMemberId(
                                segment.getId(), memberId);
        return new SegmentSummaryResponse(
                segment.getId(),
                segment.getName(),
                segment.getDescription(),
                segment.getEstimatedCount(),
                segment.getBuildStatus(),
                creatorOf(segment),
                segment.getCreatedAt(),
                segment.getUpdatedAt(),
                0,
                favorited);
    }

    private SegmentDetailResponse toDetail(Segment segment, Long memberId) {
        LinkedCampaignsView linked = linkedCampaignChecker.listLinkedCampaigns(segment.getId());
        boolean favorited =
                memberId != null
                        && segmentFavoriteRepository.existsBySegmentIdAndMemberId(
                                segment.getId(), memberId);
        return new SegmentDetailResponse(
                segment.getId(),
                segment.getName(),
                segment.getDescription(),
                segment.getConditionTree(),
                segment.getSchemaVersion(),
                segment.getEstimatedCount(),
                segment.getEstimatedAt(),
                segment.getLastBuiltAt(),
                segment.getBuildStatus(),
                creatorOf(segment),
                segment.getCreatedAt(),
                segment.getUpdatedAt(),
                flatten(linked),
                favorited);
    }

    private static CreatorSummary creatorOf(Segment segment) {
        return new CreatorSummary(segment.getCreatedBy(), null);
    }

    private static List<LinkedCampaignSummary> flatten(LinkedCampaignsView view) {
        return java.util.stream.Stream.of(view.active(), view.paused(), view.draft(), view.ended())
                .flatMap(List::stream)
                .map(c -> new LinkedCampaignSummary(c.campaignId(), c.name(), c.campaignStatus()))
                .toList();
    }
}
