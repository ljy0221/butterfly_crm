package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.segment.constant.AuditAction;
import com.b101.butterfly.segment.domain.condition.GroupNode;
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
import com.b101.butterfly.segment.service.match.ActiveEventSegmentCache;
import com.b101.butterfly.segment.service.preview.ConditionTreeHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    /** audit detail의 free-text 필드 (name/description) 길이 cap. PII 노출·detail 비대화 방지. */
    private static final int AUDIT_FREE_TEXT_CAP = 200;

    /** {@link ConditionTreeHasher}는 64-hex SHA-256을 반환. audit detail에는 앞 8자만 변경 추적용. */
    private static final int AUDIT_HASH_PREFIX_LENGTH = 8;

    private final SegmentRepository segmentRepository;
    private final SegmentFavoriteRepository segmentFavoriteRepository;
    private final ConditionTreeValidator conditionTreeValidator;
    private final LinkedCampaignChecker linkedCampaignChecker;
    private final ConditionTreeHasher conditionTreeHasher;
    private final ApplicationEventPublisher eventPublisher;
    private final ActiveEventSegmentCache activeEventSegmentCache;

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

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", truncateForAudit(saved.getName()));
        detail.put("conditionTreeHash", auditHash(projectId, saved.getConditionTree()));
        detail.put("conditionTreeVersion", saved.getSchemaVersion());
        eventPublisher.publishEvent(
                AuditRecordedEvent.of(
                        projectId,
                        createdBy,
                        AuditAction.SEGMENT_CREATED,
                        "Segment",
                        saved.getId(),
                        null,
                        null,
                        detail));

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

        // before snapshot — rename/replace 호출 전에 캡처해야 진짜 이전 값이 남는다
        String beforeName = segment.getName();
        String beforeDescription = segment.getDescription();
        String beforeHash = auditHash(projectId, segment.getConditionTree());

        segment.rename(request.name(), request.description());
        segment.replaceConditionTree(request.conditionTree());

        String afterHash = auditHash(projectId, segment.getConditionTree());
        boolean nameChanged = !Objects.equals(beforeName, segment.getName());
        boolean descriptionChanged = !Objects.equals(beforeDescription, segment.getDescription());
        boolean conditionTreeChanged = !Objects.equals(beforeHash, afterHash);

        // no-op (실제로 아무것도 바뀌지 않음)이면 audit 발행 X
        if (nameChanged || descriptionChanged || conditionTreeChanged) {
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("name", truncateForAudit(beforeName));
            before.put("description", truncateForAudit(beforeDescription));
            before.put("conditionTreeHash", beforeHash);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("name", truncateForAudit(segment.getName()));
            after.put("description", truncateForAudit(segment.getDescription()));
            after.put("conditionTreeHash", afterHash);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("after", after);
            detail.put("conditionTreeChanged", conditionTreeChanged);
            eventPublisher.publishEvent(
                    AuditRecordedEvent.of(
                            projectId,
                            memberId,
                            AuditAction.SEGMENT_UPDATED,
                            "Segment",
                            segment.getId(),
                            null,
                            null,
                            detail));
        }

        // Phase 6-3: 트리 변경 시 활성 segment 캐시 무효화 (categoryId 변경 가능성)
        if (conditionTreeChanged) {
            activeEventSegmentCache.invalidate(projectId);
        }

        return toDetail(segment, memberId);
    }

    @Transactional
    public void delete(Long projectId, Long segmentId, Long memberId) {
        Segment segment = findActiveSegment(segmentId);
        if (linkedCampaignChecker.hasActiveOrSending(segmentId)) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_409_LINKED_ACTIVE_CAMPAIGN);
        }

        String finalName = segment.getName();
        segment.softDelete();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", truncateForAudit(finalName));
        eventPublisher.publishEvent(
                AuditRecordedEvent.of(
                        projectId,
                        memberId,
                        AuditAction.SEGMENT_DELETED,
                        "Segment",
                        segment.getId(),
                        null,
                        null,
                        detail));

        // Phase 6-3: 삭제된 segment가 캐시에 남아 매칭되지 않도록 즉시 무효화
        activeEventSegmentCache.invalidate(projectId);
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

    /**
     * audit detail의 free-text 필드 길이 cap. {@code null}은 그대로, 200자 초과는 truncate.
     */
    private static String truncateForAudit(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= AUDIT_FREE_TEXT_CAP) {
            return value;
        }
        return value.substring(0, AUDIT_FREE_TEXT_CAP);
    }

    /**
     * audit detail용 conditionTree 짧은 hash. {@link ConditionTreeHasher}의 64-hex 결과 앞 8자.
     * 변경 추적 용도라 충돌 위험이 무시 가능 (같은 segment 내 단일 트리만 비교).
     */
    private String auditHash(Long projectId, GroupNode tree) {
        return conditionTreeHasher.hash(projectId, tree).substring(0, AUDIT_HASH_PREFIX_LENGTH);
    }
}
