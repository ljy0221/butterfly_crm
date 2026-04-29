package com.b101.butterfly.tenant.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ApplicationController {

    private final ApplicationService applicationService;

    // 서비스 신청서 제출 (공개)
    @PostMapping("/api/applications")
    public ResponseEntity<ApiResponse<Long>> submit(
            @Valid @RequestBody ApplicationCreateRequest request) {
        Long applicationId = applicationService.submit(request);
        return ResponseEntity.ok(ApiResponse.ok(applicationId));
    }

    // 신청서 목록 조회 (ADMIN)
    // TODO: 인증 통합 시 @PreAuthorize("@projectPermission.isAdmin()") 추가
    @GetMapping("/api/admin/applications")
    public ResponseEntity<ApiResponse<Page<ApplicationResponse>>> getList(
            @RequestParam(required = false) ApplicationStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getList(status, pageable)));
    }

    // 신청서 상세 조회 (ADMIN)
    @GetMapping("/api/admin/applications/{applicationId}")
    public ResponseEntity<ApiResponse<ApplicationResponse>> getDetail(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getDetail(applicationId)));
    }

    // 신청서 승인 (ADMIN)
    @PostMapping("/api/admin/applications/{applicationId}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable Long applicationId) {
        // TODO: 인증 통합 시 SecurityContext에서 adminMemberId 추출
        Long adminMemberId = 1L;
        applicationService.approve(applicationId, adminMemberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 신청서 반려 (ADMIN)
    @PostMapping("/api/admin/applications/{applicationId}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationRejectRequest request) {
        // TODO: 인증 통합 시 SecurityContext에서 adminMemberId 추출
        Long adminMemberId = 1L;
        applicationService.reject(applicationId, adminMemberId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
