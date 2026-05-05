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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 *
 * <p>인가: ADR-0005 — {@code @PreAuthorize("@projectPermission.hasRole(#projectId, ROLE)")}로
 * 프로젝트별 역할 검증. memberId는 SecurityContext의 {@link Authentication#getName()}에서 추출
 * (auth 모듈의 CustomUserDetails 직접 import는 모듈 슬라이스 cycle 회피 위해 사용 안 함).
 * 클라이언트 위조 방지 위해 X-Member-Id 헤더는 더 이상 신뢰하지 않음.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    @PostMapping
    public ResponseEntity<CampaignCreateResponse> createCampaign(
            @PathVariable Long projectId,
            @RequestBody @Valid CampaignCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        Long campaignId = campaignService.createCampaign(
                request.toCommand(projectId, currentMemberId()));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new CampaignCreateResponse(campaignId));
    }

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
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

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignDetailResponse> getCampaign(
            @PathVariable Long projectId,
            @PathVariable Long campaignId
    ) {
        CampaignDetailResult result = campaignService.getCampaign(projectId, campaignId);
        return ResponseEntity.ok(CampaignDetailResponse.from(result));
    }

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
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

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    @DeleteMapping("/{campaignId}")
    public ResponseEntity<Void> deleteCampaign(
            @PathVariable Long projectId,
            @PathVariable Long campaignId
    ) {
        campaignService.deleteCampaign(campaignId, projectId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
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

    /**
     * SecurityContext의 인증 정보에서 memberId 추출.
     *
     * <p>{@code CustomUserDetails.getUsername()}이 {@code String.valueOf(memberId)}를 반환하므로
     * {@link Authentication#getName()}으로 안전하게 얻는다. {@link SecurityContextHolder}를 직접 사용해
     * Spring Security 의 {@code AuthenticationArgumentResolver} 등록 여부와 무관하게 동작한다.
     * 이 우회로 campaign → auth 직접 의존을 피해 모듈 슬라이스 cycle을 차단한다.
     */
    private Long currentMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
