package com.b101.butterfly.tenant.controller;

import com.b101.butterfly.auth.security.CustomUserDetails;
import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.tenant.constant.ApplicationStatus;
import com.b101.butterfly.tenant.dto.request.ApplicationCreateRequest;
import com.b101.butterfly.tenant.dto.request.ApplicationRejectRequest;
import com.b101.butterfly.tenant.dto.response.ApplicationResponse;
import com.b101.butterfly.tenant.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ApplicationController {

    private final ApplicationService applicationService;

    // 서비스 신청서 제출 (공개)
    @PostMapping("/applications")
    public ResponseEntity<ApiResponse<Long>> submit(
            @Valid @RequestBody ApplicationCreateRequest request) {
        Long applicationId = applicationService.submit(request);
        return ResponseEntity.ok(ApiResponse.ok(applicationId));
    }

    // 신청서 목록 조회 (ADMIN)
    @GetMapping("/admin/applications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ApplicationResponse>>> getList(
            @RequestParam(required = false) ApplicationStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getList(status, pageable)));
    }

    // 신청서 상세 조회 (ADMIN)
    @GetMapping("/admin/applications/{applicationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ApplicationResponse>> getDetail(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getDetail(applicationId)));
    }

    // 신청서 승인 (ADMIN)
    @PostMapping("/admin/applications/{applicationId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        applicationService.approve(applicationId, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 신청서 반려 (ADMIN)
    @PostMapping("/admin/applications/{applicationId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ApplicationRejectRequest request) {
        applicationService.reject(applicationId, userDetails.getMemberId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
