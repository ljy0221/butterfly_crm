package com.b101.butterfly.tenant.controller;

import com.b101.butterfly.auth.security.CustomUserDetails;
import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.tenant.dto.request.UpdateMemberRoleRequest;
import com.b101.butterfly.tenant.dto.response.ApiKeyResponse;
import com.b101.butterfly.tenant.dto.response.ProjectMemberResponse;
import com.b101.butterfly.tenant.dto.response.ProjectResponse;
import com.b101.butterfly.tenant.service.ApiKeyService;
import com.b101.butterfly.tenant.service.ProjectMemberService;
import com.b101.butterfly.tenant.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;
    private final ApiKeyService apiKeyService;

    // 프로젝트 삭제 (ADMIN)
    @DeleteMapping("/admin/projects/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 프로젝트 상세 조회 (VIEWER 이상)
    @GetMapping("/projects/{projectId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getProject(projectId)));
    }

    // 멤버 목록 조회 (VIEWER 이상)
    @GetMapping("/projects/{projectId}/members")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ResponseEntity<ApiResponse<List<ProjectMemberResponse>>> getMembers(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(projectMemberService.getMembers(projectId)));
    }

    // 멤버 삭제 (OWNER 이상)
    @DeleteMapping("/projects/{projectId}/members/{memberId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        projectMemberService.removeMember(projectId, memberId, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 멤버 역할 변경 (OWNER 이상)
    @PutMapping("/projects/{projectId}/members/{memberId}/role")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ResponseEntity<ApiResponse<Void>> updateMemberRole(
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        projectMemberService.updateMemberRole(projectId, memberId, userDetails.getMemberId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // API 키 최초 발급 (OWNER 이상)
    @PostMapping("/projects/{projectId}/api-keys/issue")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> issueApiKey(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                new ApiKeyResponse(apiKeyService.issueApiKey(projectId, userDetails.getMemberId()))));
    }

    // API 키 재발급 (OWNER 이상)
    @PostMapping("/projects/{projectId}/api-keys/revoke")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> revokeApiKey(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                new ApiKeyResponse(apiKeyService.revokeAndReissueApiKey(projectId, userDetails.getMemberId()))));
    }
}
