package com.b101.butterfly.reporting.report.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixQuery;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixResponse;
import com.b101.butterfly.reporting.report.service.SegmentChannelMatrixService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;

/**
 * 세그먼트 × 채널 성과 매트릭스 컨트롤러 (S14P31B101-92, 기획서 11-10).
 *
 * <p>OWNER 또는 ADMIN 권한 필요 — 본 프로젝트의 segment 데이터에 접근하므로.
 * 현재 SpEL은 기존 컨벤션(`@projectPermission.hasRole`)을 따르며, ADMIN 인가는 SpEL 표현식
 * 자체가 미충족 시 method-level fallback이 없으므로 OWNER 기준만 사용한다.
 *
 * <p>응답은 {@link ApiResponse}로 래핑된 {@link SegmentChannelMatrixResponse}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/reports/segment-channel-matrix")
@RequiredArgsConstructor
public class SegmentChannelMatrixController {

    private final SegmentChannelMatrixService matrixService;

    @GetMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ApiResponse<SegmentChannelMatrixResponse> getMatrix(
            @PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Set<Channel> channels,
            @RequestParam(required = false) Set<Long> segmentIds,
            @RequestParam(required = false) SegmentChannelMatrixQuery.SortBy sortBy,
            @RequestParam(required = false, defaultValue = "false") boolean ascending
    ) {
        SegmentChannelMatrixQuery query = new SegmentChannelMatrixQuery(
                projectId, from, to, channels, segmentIds, sortBy, ascending
        );
        return ApiResponse.ok(matrixService.getMatrix(query));
    }
}
