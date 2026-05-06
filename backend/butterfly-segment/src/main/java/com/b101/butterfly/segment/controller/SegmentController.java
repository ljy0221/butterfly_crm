package com.b101.butterfly.segment.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.segment.dto.request.CreateSegmentRequest;
import com.b101.butterfly.segment.dto.request.PreviewRequest;
import com.b101.butterfly.segment.dto.request.UpdateSegmentRequest;
import com.b101.butterfly.segment.dto.response.CreateSegmentResponse;
import com.b101.butterfly.segment.dto.response.FavoritesResponse;
import com.b101.butterfly.segment.dto.response.LinkedCampaignsResponse;
import com.b101.butterfly.segment.dto.response.PreviewResponse;
import com.b101.butterfly.segment.dto.response.RebuildResponse;
import com.b101.butterfly.segment.dto.response.SegmentDetailResponse;
import com.b101.butterfly.segment.dto.response.SegmentSummaryResponse;
import com.b101.butterfly.segment.dto.response.SegmentUserResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 세그먼트 API. 03-segment.md §1~2 12개 엔드포인트.
 *
 * <p>Phase 4 — CRUD/즐겨찾기는 서비스 호출. Preview/Users/Rebuild/Campaigns/Favorites는 Phase 5~8.
 *
 * <p>{@code X-Member-Id} 헤더는 auth 모듈 합류 전 임시 인증. 추후 {@code @AuthenticationPrincipal}로 교체.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/segments")
@RequiredArgsConstructor
public class SegmentController {

    private final SegmentService segmentService;
    private final SegmentFavoriteService segmentFavoriteService;
    private final PreviewService previewService;
    private final SegmentUsersService segmentUsersService;

    /**
     * {@link SegmentRebuildService}는 {@code @Profile("api")}로 worker에서는 등록되지 않는다.
     * Controller는 worker에도 빈 등록되므로 {@link ObjectProvider}로 lazy resolution — 실제 호출은
     * api 프로파일에서만 발생.
     */
    private final ObjectProvider<SegmentRebuildService> segmentRebuildServiceProvider;

    @PostMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ResponseEntity<ApiResponse<CreateSegmentResponse>> create(
            @PathVariable Long projectId,
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody CreateSegmentRequest request) {
        CreateSegmentResponse response = segmentService.create(projectId, memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<PageResponse<SegmentSummaryResponse>> list(
            @PathVariable Long projectId,
            @RequestHeader("X-Member-Id") Long memberId,
            Pageable pageable) {
        return ApiResponse.ok(segmentService.list(projectId, memberId, pageable));
    }

    @GetMapping("/{segmentId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<SegmentDetailResponse> detail(
            @PathVariable Long projectId,
            @PathVariable Long segmentId,
            @RequestHeader("X-Member-Id") Long memberId) {
        return ApiResponse.ok(segmentService.detail(projectId, segmentId, memberId));
    }

    @PutMapping("/{segmentId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<SegmentDetailResponse> update(
            @PathVariable Long projectId,
            @PathVariable Long segmentId,
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody UpdateSegmentRequest request) {
        return ApiResponse.ok(segmentService.update(projectId, segmentId, memberId, request));
    }

    @DeleteMapping("/{segmentId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long segmentId,
            @RequestHeader("X-Member-Id") Long memberId) {
        segmentService.delete(projectId, segmentId, memberId);
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
            @PathVariable Long projectId,
            @PathVariable Long segmentId,
            @RequestHeader("X-Member-Id") Long memberId) {
        RebuildResponse response =
                segmentRebuildServiceProvider
                        .getObject()
                        .requestRebuild(projectId, segmentId, memberId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
    }

    @GetMapping("/{segmentId}/campaigns")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<LinkedCampaignsResponse> linkedCampaigns(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        // TODO Phase 7 — LinkedCampaignChecker.listLinkedCampaigns 변환
        return ApiResponse.ok(
                new LinkedCampaignsResponse(List.of(), List.of(), List.of(), List.of(), 0));
    }

    @GetMapping("/favorites")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<FavoritesResponse> favorites(
            @PathVariable Long projectId,
            @RequestHeader("X-Member-Id") Long memberId) {
        return ApiResponse.ok(segmentFavoriteService.list(projectId, memberId));
    }

    @PostMapping("/{segmentId}/favorite")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<Void> addFavorite(
            @PathVariable Long projectId,
            @PathVariable Long segmentId,
            @RequestHeader("X-Member-Id") Long memberId) {
        segmentFavoriteService.addFavorite(projectId, segmentId, memberId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{segmentId}/favorite")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<Void> removeFavorite(
            @PathVariable Long projectId,
            @PathVariable Long segmentId,
            @RequestHeader("X-Member-Id") Long memberId) {
        segmentFavoriteService.removeFavorite(projectId, segmentId, memberId);
        return ApiResponse.ok();
    }
}
