package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.request.CreateCategoryRequest;
import com.b101.butterfly.event.dto.request.UpdateSchemaRequest;
import com.b101.butterfly.event.dto.response.DeleteCategoryResponse;
import com.b101.butterfly.event.dto.response.EventCategoryDetailResponse;
import com.b101.butterfly.event.dto.response.EventCategorySummaryResponse;
import com.b101.butterfly.event.dto.response.PropertyResponse;
import com.b101.butterfly.event.dto.response.RecentSampleResponse;
import com.b101.butterfly.event.service.EventCategoryService;
import com.b101.butterfly.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 이벤트 카테고리 + 스키마 CRUD endpoint.
 *
 * <p>frontend {@code /api/v1/projects/{projectId}/events/categories} 7 종 호출을 지원한다.
 * 기존 {@code EventSchemaController} (categoryId 기반 /event-categories/.../schemas) 는 그대로 유지되며
 * 본 컨트롤러는 categoryName 기반 / 프론트 경로에 맞춰 새로 신설된다.
 *
 * <p>{@code @PreAuthorize} 는 auth 통합 후 활성화 (다른 event 컨트롤러와 동일한 TODO 패턴).
 */
@RestController
@Profile("api")
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/events/categories")
public class EventCategoryController {

    private final EventCategoryService eventCategoryService;

    // TODO: @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')") — auth 통합 후 활성화
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventCategorySummaryResponse>>> list(
            @PathVariable Long projectId) {
        List<EventCategorySummaryResponse> response = eventCategoryService.list(projectId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // TODO: @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')") — auth 통합 후 활성화
    @GetMapping("/{categoryName}")
    public ResponseEntity<ApiResponse<EventCategoryDetailResponse>> get(
            @PathVariable Long projectId,
            @PathVariable String categoryName) {
        EventCategoryDetailResponse response = eventCategoryService.get(projectId, categoryName);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // TODO: @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')") — auth 통합 후 활성화
    @GetMapping("/{categoryName}/schema")
    public ResponseEntity<ApiResponse<List<PropertyResponse>>> getSchema(
            @PathVariable Long projectId,
            @PathVariable String categoryName) {
        List<PropertyResponse> response = eventCategoryService.getSchema(projectId, categoryName);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // TODO: @PreAuthorize("@projectPermission.hasRole(#projectId, 'ADMIN')") — auth 통합 후 활성화
    @PostMapping
    public ResponseEntity<ApiResponse<EventCategoryDetailResponse>> create(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") Long createdBy,
            @RequestBody @Valid CreateCategoryRequest request) {
        EventCategoryDetailResponse response =
                eventCategoryService.create(projectId, createdBy, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // TODO: @PreAuthorize("@projectPermission.hasRole(#projectId, 'ADMIN')") — auth 통합 후 활성화
    @PutMapping("/{categoryName}/schema")
    public ResponseEntity<ApiResponse<List<PropertyResponse>>> updateSchema(
            @PathVariable Long projectId,
            @PathVariable String categoryName,
            @RequestParam(defaultValue = "0") Long createdBy,
            @RequestBody @Valid UpdateSchemaRequest request) {
        List<PropertyResponse> response =
                eventCategoryService.updateSchema(projectId, categoryName, createdBy, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // TODO: @PreAuthorize("@projectPermission.hasRole(#projectId, 'ADMIN')") — auth 통합 후 활성화
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<DeleteCategoryResponse>> delete(
            @PathVariable Long projectId,
            @PathVariable Long categoryId) {
        DeleteCategoryResponse response = eventCategoryService.delete(projectId, categoryId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // TODO: @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')") — auth 통합 후 활성화
    @GetMapping("/{categoryName}/sample")
    public ResponseEntity<ApiResponse<RecentSampleResponse>> getRecentSample(
            @PathVariable Long projectId,
            @PathVariable String categoryName) {
        RecentSampleResponse response = eventCategoryService.getRecentSample(projectId, categoryName);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
