package com.b101.butterfly.segment.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.segment.dto.request.CreateSegmentRequest;
import com.b101.butterfly.segment.dto.request.PreviewRequest;
import com.b101.butterfly.segment.dto.request.UpdateSegmentRequest;
import com.b101.butterfly.segment.dto.response.CancelBuildResponse;
import com.b101.butterfly.segment.dto.response.CreateSegmentResponse;
import com.b101.butterfly.segment.dto.response.FavoritesResponse;
import com.b101.butterfly.segment.dto.response.LinkedCampaignsResponse;
import com.b101.butterfly.segment.dto.response.PreviewResponse;
import com.b101.butterfly.segment.dto.response.RebuildResponse;
import com.b101.butterfly.segment.dto.response.SegmentConditionMetadataResponse;
import com.b101.butterfly.segment.dto.response.SegmentDetailResponse;
import com.b101.butterfly.segment.dto.response.SegmentSummaryResponse;
import com.b101.butterfly.segment.dto.response.SegmentUserResponse;
import com.b101.butterfly.segment.service.SegmentBuildCancelService;
import com.b101.butterfly.segment.service.SegmentConditionMetadataService;
import com.b101.butterfly.segment.service.SegmentFavoriteService;
import com.b101.butterfly.segment.service.SegmentRebuildService;
import com.b101.butterfly.segment.service.SegmentService;
import com.b101.butterfly.segment.service.SegmentUsersService;
import com.b101.butterfly.segment.service.preview.PreviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세그먼트 API. 03-segment.md §1~2 12개 엔드포인트.
 *
 * <p>Phase 4 — CRUD/즐겨찾기는 서비스 호출. Preview/Users/Rebuild/Campaigns/Favorites는 Phase 5~8.
 *
 * <p>인증: {@link SecurityContextHolder} 의 {@link Authentication#getName()} 에서 memberId 추출.
 * {@code butterfly-segment} 는 {@code butterfly-auth} 의존이 없어 {@code CustomUserDetails} 직접
 * import 불가 — 가이드(2026-05-12)에 따라 SecurityContext 방식 채택.
 * 로컬 테스트에서는 {@code LocalAuthFilter} 가 {@code X-Member-Id} 헤더로 SecurityContext 를
 * 채워주므로 호출 측 동작은 동일.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/segments")
@RequiredArgsConstructor
public class SegmentController {

    private final SegmentService segmentService;
    private final SegmentConditionMetadataService segmentConditionMetadataService;
    private final SegmentFavoriteService segmentFavoriteService;
    private final PreviewService previewService;
    private final SegmentUsersService segmentUsersService;

    /**
     * {@link SegmentRebuildService}는 {@code @Profile("api")}로 worker에서는 등록되지 않는다.
     * Controller는 worker에도 빈 등록되므로 {@link ObjectProvider}로 lazy resolution — 실제 호출은
     * api 프로파일에서만 발생.
     */
    private final ObjectProvider<SegmentRebuildService> segmentRebuildServiceProvider;

    /** {@link SegmentBuildCancelService} 도 동일한 패턴 — api 프로파일에만 등록 (S14P31B101-269). */
    private final ObjectProvider<SegmentBuildCancelService> segmentBuildCancelServiceProvider;

    @PostMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ResponseEntity<ApiResponse<CreateSegmentResponse>> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateSegmentRequest request) {
        CreateSegmentResponse response =
                segmentService.create(projectId, currentMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<PageResponse<SegmentSummaryResponse>> list(
            @PathVariable Long projectId, Pageable pageable) {
        return ApiResponse.ok(segmentService.list(projectId, currentMemberId(), pageable));
    }

    @GetMapping("/condition-metadata")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<SegmentConditionMetadataResponse> conditionMetadata(
            @PathVariable Long projectId) {
        return ApiResponse.ok(segmentConditionMetadataService.getMetadata());
    }

    @GetMapping("/{segmentId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<SegmentDetailResponse> detail(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        return ApiResponse.ok(segmentService.detail(projectId, segmentId, currentMemberId()));
    }

    @PutMapping("/{segmentId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<SegmentDetailResponse> update(
            @PathVariable Long projectId,
            @PathVariable Long segmentId,
            @Valid @RequestBody UpdateSegmentRequest request) {
        return ApiResponse.ok(
                segmentService.update(projectId, segmentId, currentMemberId(), request));
    }

    @DeleteMapping("/{segmentId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<Void> delete(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        segmentService.delete(projectId, segmentId, currentMemberId());
        return ApiResponse.ok();
    }

    @PostMapping("/preview")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<PreviewResponse> preview(
            @PathVariable Long projectId, @Valid @RequestBody PreviewRequest request) {
        return ApiResponse.ok(previewService.preview(projectId, request.conditionTree()));
    }

    @GetMapping("/{segmentId}/users")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<PageResponse<SegmentUserResponse>> users(
            @PathVariable Long projectId,
            @PathVariable Long segmentId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(segmentUsersService.listUsers(projectId, segmentId, pageable));
    }

    @PostMapping("/{segmentId}/rebuild")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ResponseEntity<ApiResponse<RebuildResponse>> rebuild(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        RebuildResponse response =
                segmentRebuildServiceProvider
                        .getObject()
                        .requestRebuild(projectId, segmentId, currentMemberId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
    }

    /**
     * 빌드 취소 (S14P31B101-269). BUILDING/PENDING 상태의 빌드를 즉시 FAILED 로 강등하고 cancel
     * marker 를 박는다. UX 측에는 즉시 FAILED 로 노출되므로 stale UI 문제 회피.
     */
    @PostMapping("/{segmentId}/build/cancel")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<CancelBuildResponse> cancelBuild(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        CancelBuildResponse response =
                segmentBuildCancelServiceProvider
                        .getObject()
                        .cancelBuild(projectId, segmentId, currentMemberId());
        return ApiResponse.ok(response);
    }

    @GetMapping("/{segmentId}/campaigns")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<LinkedCampaignsResponse> linkedCampaigns(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        return ApiResponse.ok(segmentService.listLinkedCampaigns(projectId, segmentId));
    }

    @GetMapping("/favorites")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<FavoritesResponse> favorites(@PathVariable Long projectId) {
        return ApiResponse.ok(segmentFavoriteService.list(projectId, currentMemberId()));
    }

    @PostMapping("/{segmentId}/favorite")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<Void> addFavorite(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        segmentFavoriteService.addFavorite(projectId, segmentId, currentMemberId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{segmentId}/favorite")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<Void> removeFavorite(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        segmentFavoriteService.removeFavorite(projectId, segmentId, currentMemberId());
        return ApiResponse.ok();
    }

    /**
     * SecurityContext 의 인증 정보에서 memberId 를 추출한다.
     *
     * <p>auth 모듈의 {@code CustomUserDetails.getUsername()} 이 {@code String.valueOf(memberId)} 를
     * 반환하므로 {@link Authentication#getName()} 으로 안전하게 얻는다. {@link SecurityContextHolder}
     * 를 직접 사용해 Spring Security 의 {@code AuthenticationArgumentResolver} 등록 여부와 무관하게
     * 동작하며, segment → auth 직접 의존을 피해 모듈 슬라이스 cycle 을 차단한다.
     */
    private Long currentMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
