package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.response.RecommendedVariableResponse;
import com.b101.butterfly.event.service.PersonalizationVariableService;
import com.b101.butterfly.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메시지 템플릿 편집 모달의 "추천 변수" 칩 패널 데이터 진입점.
 *
 * <p>{@code segmentId} 미지정 → 프로젝트 전체 user_tags 의 distinct tag_key 빈도 상위 N.
 * {@code segmentId} 지정 → 활성 세그먼트 멤버 한정 동일 집계 (만료 멤버 제외).
 *
 * <p>인가: ADR-0005 — VIEWER 이상.
 */
@RestController
@Profile("api")
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/personalization-variables")
public class PersonalizationVariableController {

    private final PersonalizationVariableService personalizationVariableService;

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    @GetMapping
    public ResponseEntity<ApiResponse<RecommendedVariableResponse>> getRecommended(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long segmentId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(personalizationVariableService.getRecommended(projectId, segmentId)));
    }
}
