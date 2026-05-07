package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.request.EventSchemaCreateRequest;
import com.b101.butterfly.event.dto.response.EventSchemaResponse;
import com.b101.butterfly.event.service.EventSchemaService;
import com.b101.butterfly.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/event-categories/{categoryId}/schemas")
public class EventSchemaController {

    private final EventSchemaService eventSchemaService;

    // TODO: @PreAuthorize("@projectPermission.hasRole(#projectId, 'ADMIN')") — auth 통합 후 활성화
    @PostMapping
    public ResponseEntity<ApiResponse<EventSchemaResponse>> createSchema(
            @PathVariable Long projectId,
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") Long createdBy,
            @RequestBody @Valid EventSchemaCreateRequest request
    ) {
        EventSchemaResponse response = eventSchemaService.createSchema(projectId, categoryId, createdBy, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // TODO: @PreAuthorize("@projectPermission.hasRole(#projectId, 'ADMIN')") — auth 통합 후 활성화
    @PutMapping
    public ResponseEntity<ApiResponse<EventSchemaResponse>> updateSchema(
            @PathVariable Long projectId,
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") Long createdBy,
            @RequestBody @Valid EventSchemaCreateRequest request
    ) {
        EventSchemaResponse response = eventSchemaService.updateSchema(projectId, categoryId, createdBy, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // TODO: @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')") — auth 통합 후 활성화
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<EventSchemaResponse>> getCurrentSchema(
            @PathVariable Long projectId,
            @PathVariable Long categoryId
    ) {
        EventSchemaResponse response = eventSchemaService.getCurrentSchema(projectId, categoryId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // TODO: @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')") — auth 통합 후 활성화
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventSchemaResponse>>> listSchemas(
            @PathVariable Long projectId,
            @PathVariable Long categoryId
    ) {
        List<EventSchemaResponse> response = eventSchemaService.listSchemas(projectId, categoryId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
