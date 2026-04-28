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
import com.b101.butterfly.segment.entity.BuildStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 세그먼트 API. 03-segment.md §1~2 12개 엔드포인트.
 *
 * <p>Phase 3 단계 — 라우팅/DTO/응답 shape만 고정. 실제 비즈니스 로직은 Phase 4에서 채움.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/segments")
public class SegmentController {

    @PostMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ResponseEntity<ApiResponse<CreateSegmentResponse>> create(
            @PathVariable Long projectId, @Valid @RequestBody CreateSegmentRequest request) {
        // TODO Phase 4 — SegmentService.create 호출
        CreateSegmentResponse stub =
                new CreateSegmentResponse(
                        0L,
                        request.name(),
                        request.description(),
                        BuildStatus.PENDING,
                        null,
                        null,
                        null,
                        3,
                        0L,
                        LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(stub));
    }

    @GetMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<PageResponse<SegmentSummaryResponse>> list(
            @PathVariable Long projectId, Pageable pageable) {
        // TODO Phase 4 — SegmentService.list 호출
        return ApiResponse.ok(
                PageResponse.of(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0));
    }

    @GetMapping("/{segmentId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<SegmentDetailResponse> detail(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        // TODO Phase 4 — SegmentService.detail 호출
        return ApiResponse.ok(
                new SegmentDetailResponse(
                        segmentId,
                        "(stub)",
                        null,
                        null,
                        3,
                        null,
                        null,
                        null,
                        BuildStatus.PENDING,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        List.of(),
                        false));
    }

    @PutMapping("/{segmentId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<SegmentDetailResponse> update(
            @PathVariable Long projectId,
            @PathVariable Long segmentId,
            @Valid @RequestBody UpdateSegmentRequest request) {
        // TODO Phase 4 — SegmentService.update 호출 + LinkedCampaignChecker 검증
        return ApiResponse.ok(
                new SegmentDetailResponse(
                        segmentId,
                        request.name(),
                        request.description(),
                        request.conditionTree(),
                        3,
                        null,
                        null,
                        null,
                        BuildStatus.PENDING,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        List.of(),
                        false));
    }

    @DeleteMapping("/{segmentId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<Void> delete(@PathVariable Long projectId, @PathVariable Long segmentId) {
        // TODO Phase 4 — SegmentService.delete (soft) + LinkedCampaignChecker 검증
        return ApiResponse.ok();
    }

    @PostMapping("/preview")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<PreviewResponse> preview(
            @PathVariable Long projectId, @Valid @RequestBody PreviewRequest request) {
        // TODO Phase 5 — PreviewService 호출 (HLL TAG/USER/DEVICE 한정)
        return ApiResponse.ok(new PreviewResponse(0, "HLL", LocalDateTime.now()));
    }

    @GetMapping("/{segmentId}/users")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<PageResponse<SegmentUserResponse>> users(
            @PathVariable Long projectId,
            @PathVariable Long segmentId,
            Pageable pageable) {
        // TODO Phase 8-1 — SegmentMember 페이지 + customer.users JOIN
        return ApiResponse.ok(
                PageResponse.of(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0));
    }

    @PostMapping("/{segmentId}/rebuild")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ResponseEntity<ApiResponse<RebuildResponse>> rebuild(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        // TODO Phase 6 — buildStatus CAS + 큐 publish
        RebuildResponse stub = new RebuildResponse(segmentId, BuildStatus.BUILDING, LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(stub));
    }

    @GetMapping("/{segmentId}/campaigns")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<LinkedCampaignsResponse> linkedCampaigns(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        // TODO Phase 7 — LinkedCampaignChecker.listLinkedCampaigns
        return ApiResponse.ok(
                new LinkedCampaignsResponse(List.of(), List.of(), List.of(), List.of(), 0));
    }

    @GetMapping("/favorites")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<FavoritesResponse> favorites(@PathVariable Long projectId) {
        // TODO Phase 8-2 — favorites + recentlyUsed
        return ApiResponse.ok(new FavoritesResponse(List.of(), List.of()));
    }

    @PostMapping("/{segmentId}/favorite")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<Void> addFavorite(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        // TODO Phase 4 — SegmentFavoriteService.addFavorite (멱등)
        return ApiResponse.ok();
    }

    @DeleteMapping("/{segmentId}/favorite")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<Void> removeFavorite(
            @PathVariable Long projectId, @PathVariable Long segmentId) {
        // TODO Phase 4 — SegmentFavoriteService.removeFavorite (멱등)
        return ApiResponse.ok();
    }
}
