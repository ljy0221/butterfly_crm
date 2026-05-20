package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.response.UserListItemResponse;
import com.b101.butterfly.event.dto.response.UserProfileResponse;
import com.b101.butterfly.event.service.UserProfileService;
import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("api")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    @GetMapping("/api/v1/projects/{projectId}/users")
    public ResponseEntity<ApiResponse<PageResponse<UserListItemResponse>>> listUsers(
            @PathVariable Long projectId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        PageResponse<UserListItemResponse> response =
                userProfileService.listUsers(projectId, search, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    @GetMapping("/api/v1/projects/{projectId}/users/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @PathVariable Long projectId,
            @PathVariable Long userId
    ) {
        UserProfileResponse response = userProfileService.getProfile(projectId, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
