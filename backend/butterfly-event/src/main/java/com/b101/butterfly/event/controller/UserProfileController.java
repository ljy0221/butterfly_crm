package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.response.UserProfileResponse;
import com.b101.butterfly.event.service.UserProfileService;
import com.b101.butterfly.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("api")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

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
