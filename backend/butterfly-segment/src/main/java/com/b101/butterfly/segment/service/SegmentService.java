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
import com.b101.butterfly.segment.dto.response.LinkedCampaignsResponse;
import com.b101.butterfly.segment.dto.response.SegmentAudienceStatsResponse;
import com.b101.butterfly.segment.dto.response.SegmentDetailResponse;
import com.b101.butterfly.segment.dto.response.SegmentSummaryResponse;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.messaging.SegmentRebuildAcceptedEvent;
import com.b101.butterfly.segment.port.LinkedCampaignChecker;
import com.b101.butterfly.segment.port.LinkedCampaignsView;
import com.b101.butterfly.segment.repository.SegmentFavoriteRepository;
import com.b101.butterfly.segment.repository.SegmentAudienceStatsReader;
import com.b101.butterfly.segment.repository.SegmentCreatorReader;
import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.service.match.ActiveEventSegmentCache;
import com.b101.butterfly.segment.service.preview.ConditionTreeHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final SegmentMemberRepository segmentMemberRepository;
    private final SegmentFavoriteRepository segmentFavoriteRepository;
    private final SegmentAudienceStatsReader segmentAudienceStatsReader;
    private final SegmentCreatorReader segmentCreatorReader;
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
        String fullHash = conditionTreeHasher.hash(projectId, saved.getConditionTree());
        detail.put(
                "conditionTreeHash",
                fullHash.substring(0, AUDIT_HASH_PREFIX_LENGTH));
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

        // S14P31B101-275 — 생성 직후 자동 빌드 trigger. AFTER_COMMIT listener 가 Kafka publish 하므로
        // segment 저장이 commit 된 후에만 worker 가 메시지 받음 (lost update / race 차단 흐름).
        eventPublisher.publishEvent(
                new SegmentRebuildAcceptedEvent(
                        saved.getId(), projectId, createdBy, Instant.now(), fullHash));

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
        Map<Long, SegmentAudienceStatsResponse> statsBySegmentId =
                segmentAudienceStatsReader.countBySegmentIds(
                        projectId,
                        page.getContent().stream().map(Segment::getId).toList());
        Map<Long, String> creatorNamesByMemberId =
                segmentCreatorReader.findNamesByMemberIds(creatorMemberIds(page.getContent()));
        List<SegmentSummaryResponse> content =
                page.getContent().stream()
                        .map(s -> toSummary(
                                s,
                                memberId,
                                statsBySegmentId.getOrDefault(
                                        s.getId(), SegmentAudienceStatsResponse.zero()),
                                creatorNamesByMemberId))
                        .toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public SegmentDetailResponse detail(Long projectId, Long segmentId, Long memberId) {
        Segment segment = findActiveSegment(segmentId);
        SegmentAudienceStatsResponse audienceStats =
                segmentAudienceStatsReader
                        .countBySegmentIds(projectId, List.of(segment.getId()))
                        .getOrDefault(segment.getId(), SegmentAudienceStatsResponse.zero());
        return toDetail(segment, memberId, audienceStats, creatorNameMap(segment));
    }

    /**
     * 세그먼트에 연결된 캠페인을 상태별로 그룹화해 반환 (API #45).
     *
     * <p>{@link #toDetail} 의 flatten 과 달리 status 별 리스트를 분리한 채로 노출 — 화면에서 status
     * 별 섹션 렌더링용. campaign 모듈 미합류 시 {@code StubLinkedCampaignChecker} 가 빈 결과 반환.
     */
    public LinkedCampaignsResponse listLinkedCampaigns(Long projectId, Long segmentId) {
        Segment segment = findActiveSegment(segmentId);
        LinkedCampaignsView view = linkedCampaignChecker.listLinkedCampaigns(segment.getId());
        return new LinkedCampaignsResponse(
                view.active().stream().map(SegmentService::toDtoSummary).toList(),
                view.paused().stream().map(SegmentService::toDtoSummary).toList(),
                view.draft().stream().map(SegmentService::toDtoSummary).toList(),
                view.ended().stream().map(SegmentService::toDtoSummary).toList(),
                view.totalCount());
    }

    private static LinkedCampaignSummary toDtoSummary(
            com.b101.butterfly.segment.port.LinkedCampaignSummary portSummary) {
        return new LinkedCampaignSummary(
                portSummary.campaignId(), portSummary.name(), portSummary.campaignStatus());
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
        String beforeFullHash = conditionTreeHasher.hash(projectId, segment.getConditionTree());
        String beforeHash = auditHash(beforeFullHash);

        String afterFullHash = conditionTreeHasher.hash(projectId, request.conditionTree());
        String afterHash = auditHash(afterFullHash);
        boolean nameChanged = !Objects.equals(beforeName, request.name());
        boolean descriptionChanged = !Objects.equals(beforeDescription, request.description());
        boolean conditionTreeChanged = !Objects.equals(beforeFullHash, afterFullHash);

        segment.rename(request.name(), request.description());
        if (conditionTreeChanged) {
            segment.replaceConditionTree(request.conditionTree());
        }

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

        // 트리 변경 시 매칭 캐시를 비우고, 커밋 이후 worker rebuild를 요청한다.
        if (conditionTreeChanged) {
            activeEventSegmentCache.invalidate(projectId);
            eventPublisher.publishEvent(
                    new SegmentRebuildAcceptedEvent(
                            segment.getId(), projectId, memberId, Instant.now(), afterFullHash));
        }

        SegmentAudienceStatsResponse audienceStats =
                segmentAudienceStatsReader
                        .countBySegmentIds(projectId, List.of(segment.getId()))
                        .getOrDefault(segment.getId(), SegmentAudienceStatsResponse.zero());
        return toDetail(segment, memberId, audienceStats, creatorNameMap(segment));
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

    private SegmentSummaryResponse toSummary(
            Segment segment,
            Long memberId,
            SegmentAudienceStatsResponse audienceStats,
            Map<Long, String> creatorNamesByMemberId
    ) {
        boolean favorited =
                memberId != null
                        && segmentFavoriteRepository.existsBySegmentIdAndMemberId(
                                segment.getId(), memberId);
        return new SegmentSummaryResponse(
                segment.getId(),
                segment.getName(),
                segment.getDescription(),
                segment.getEstimatedCount(),
                audienceStats,
                segment.getBuildStatus(),
                creatorOf(segment, creatorNamesByMemberId),
                segment.getCreatedAt(),
                segment.getUpdatedAt(),
                linkedCampaignChecker.countLinkedCampaigns(segment.getId()),
                favorited);
    }

    private SegmentDetailResponse toDetail(
            Segment segment,
            Long memberId,
            SegmentAudienceStatsResponse audienceStats,
            Map<Long, String> creatorNamesByMemberId
    ) {
        LinkedCampaignsView linked = linkedCampaignChecker.listLinkedCampaigns(segment.getId());
        boolean favorited =
                memberId != null
                        && segmentFavoriteRepository.existsBySegmentIdAndMemberId(
                                segment.getId(), memberId);
        // BUILDING/PENDING 일 때만 실시간 COUNT(*) — 그 외엔 stable estimatedCount 사용 (불필요한 비용 회피).
        Long currentMemberCount;
        if (segment.getBuildStatus() == com.b101.butterfly.segment.entity.BuildStatus.BUILDING
                || segment.getBuildStatus() == com.b101.butterfly.segment.entity.BuildStatus.PENDING) {
            currentMemberCount = segmentMemberRepository.countBySegmentId(segment.getId());
        } else {
            currentMemberCount =
                    segment.getEstimatedCount() == null
                            ? null
                            : segment.getEstimatedCount().longValue();
        }
        return new SegmentDetailResponse(
                segment.getId(),
                segment.getName(),
                segment.getDescription(),
                segment.getConditionTree(),
                segment.getSchemaVersion(),
                segment.getEstimatedCount(),
                audienceStats,
                segment.getEstimatedAt(),
                segment.getLastBuiltAt(),
                segment.getBuildStatus(),
                currentMemberCount,
                segment.getBuildStartedAt(),
                creatorOf(segment, creatorNamesByMemberId),
                segment.getCreatedAt(),
                segment.getUpdatedAt(),
                flatten(linked),
                favorited);
    }

    private Map<Long, String> creatorNameMap(Segment segment) {
        return segment.getCreatedBy() == null
                ? Map.of()
                : segmentCreatorReader.findNamesByMemberIds(List.of(segment.getCreatedBy()));
    }

    private static List<Long> creatorMemberIds(List<Segment> segments) {
        return segments.stream()
                .map(Segment::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static CreatorSummary creatorOf(Segment segment, Map<Long, String> creatorNamesByMemberId) {
        return new CreatorSummary(
                segment.getCreatedBy(),
                creatorNamesByMemberId.get(segment.getCreatedBy()));
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
    private static String auditHash(String fullHash) {
        return fullHash.substring(0, AUDIT_HASH_PREFIX_LENGTH);
    }
}
