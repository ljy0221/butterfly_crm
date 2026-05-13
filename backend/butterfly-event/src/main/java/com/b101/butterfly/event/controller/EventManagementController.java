package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.response.ProjectEventResponse;
import com.b101.butterfly.event.dto.response.UserEventResponse;
import com.b101.butterfly.event.service.EventManagementService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("api")
@RequiredArgsConstructor
public class EventManagementController {

    private final EventManagementService eventManagementService;

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    @GetMapping("/api/v1/projects/{projectId}/users/{userId}/events")
    public ResponseEntity<ApiResponse<PageResponse<UserEventResponse>>> getUserEvents(
            @PathVariable Long projectId,
            @PathVariable String userId,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        PageResponse<UserEventResponse> response =
                eventManagementService.getUserEvents(projectId, userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    @GetMapping("/api/v1/projects/{projectId}/events")
    public ResponseEntity<ApiResponse<PageResponse<ProjectEventResponse>>> getProjectEvents(
            @PathVariable Long projectId,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        PageResponse<ProjectEventResponse> response =
                eventManagementService.getProjectEvents(projectId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
