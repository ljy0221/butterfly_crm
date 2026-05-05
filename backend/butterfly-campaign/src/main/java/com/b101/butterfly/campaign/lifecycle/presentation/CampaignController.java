package com.b101.butterfly.campaign.lifecycle.presentation;

import com.b101.butterfly.campaign.lifecycle.application.CampaignService;
import com.b101.butterfly.campaign.lifecycle.application.result.CampaignDetailResult;
import com.b101.butterfly.campaign.lifecycle.application.result.CampaignListResult;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.lifecycle.presentation.dto.CampaignCreateRequest;
import com.b101.butterfly.campaign.lifecycle.presentation.dto.CampaignCreateResponse;
import com.b101.butterfly.campaign.lifecycle.presentation.dto.CampaignDetailResponse;
import com.b101.butterfly.campaign.lifecycle.presentation.dto.CampaignUpdateRequest;
import com.b101.butterfly.campaign.lifecycle.presentation.dto.StatusTransitionRequest;
import com.b101.butterfly.campaign.lifecycle.presentation.dto.StatusTransitionResponse;
import com.b101.butterfly.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캠페인 도메인의 REST 진입점.
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>URL에 동사 X — 상태 전이는 sub-resource {@code /status} + PATCH로 통합</li>
 *   <li>일반 필드 수정(PATCH .../{id})과 상태 전이(PATCH .../{id}/status)를 endpoint로 분리해 권한 분기 명확</li>
 *   <li>Idempotency-Key HTTP 헤더 수신(Stripe 패턴) — 클라이언트 자동 재시도 안전화</li>
 *   <li>Bean Validation으로 본문 모호성 제거 — 잘못된 enum/blank는 400</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    // TODO memberId 추출 + @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')") 추가
    @PostMapping
    public ResponseEntity<CampaignCreateResponse> createCampaign(
            Long memberId,
            @PathVariable Long projectId,
            @RequestBody @Valid CampaignCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        Long campaignId = campaignService.createCampaign(
                request.toCommand(projectId, memberId));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new CampaignCreateResponse(campaignId));
    }

    // TODO @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')") 추가
    @GetMapping
    public ResponseEntity<PageResponse<CampaignListResult>> getCampaignList(
            @PathVariable Long projectId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<CampaignListResult> page = campaignService.getCampaignList(projectId, pageable);
        return ResponseEntity.ok(PageResponse.of(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        ));
    }

    // TODO @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')") 추가
    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignDetailResponse> getCampaign(
            @PathVariable Long projectId,
            @PathVariable Long campaignId
    ) {
        CampaignDetailResult result = campaignService.getCampaign(projectId, campaignId);
        return ResponseEntity.ok(CampaignDetailResponse.from(result));
    }

    // TODO @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')") 추가
    @PatchMapping("/{campaignId}")
    public ResponseEntity<CampaignCreateResponse> updateCampaign(
            @PathVariable Long projectId,
            @PathVariable Long campaignId,
            @RequestBody @Valid CampaignUpdateRequest request
    ) {
        Long updatedId = campaignService.updateCampaign(
                request.toCommand(projectId, campaignId));
        return ResponseEntity.ok(new CampaignCreateResponse(updatedId));
    }

    // TODO @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')") 추가
    @DeleteMapping("/{campaignId}")
    public ResponseEntity<Void> deleteCampaign(
            @PathVariable Long projectId,
            @PathVariable Long campaignId
    ) {
        campaignService.deleteCampaign(campaignId, projectId);
        return ResponseEntity.noContent().build();
    }

    // TODO @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')") 추가
    @PatchMapping("/{campaignId}/status")
    public ResponseEntity<StatusTransitionResponse> transitionStatus(
            @PathVariable Long projectId,
            @PathVariable Long campaignId,
            @RequestBody @Valid StatusTransitionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        CampaignStatus newStatus = campaignService.transitionStatus(
                request.toCommand(projectId, campaignId));
        return ResponseEntity.ok(new StatusTransitionResponse(campaignId, newStatus));
    }
}
