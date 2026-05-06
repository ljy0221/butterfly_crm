package com.b101.butterfly.reporting.aggregation.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.reporting.aggregation.dto.DailyRollupRequest;
import com.b101.butterfly.reporting.aggregation.dto.DailyRollupResponse;
import com.b101.butterfly.reporting.aggregation.service.DailyAggregator;
import com.b101.butterfly.reporting.notification.controller.InternalTokenGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Daily 롤업 수동 트리거 엔드포인트 (API #29 — butterfly_api_spec_v2.md).
 *
 * <p>운영 도구나 재처리에 사용한다. {@link InternalTokenGuard}로 외부 노출을 차단한다.
 */
@RestController
@RequestMapping("/internal/stats/rollup/daily")
@RequiredArgsConstructor
public class DailyAggregatorController {

    private final DailyAggregator dailyAggregator;
    private final InternalTokenGuard internalTokenGuard;

    @PostMapping
    public ApiResponse<DailyRollupResponse> trigger(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @Valid @RequestBody DailyRollupRequest request
    ) {
        internalTokenGuard.verify(internalToken);
        DailyRollupResponse result = dailyAggregator.rollup(request.bucketDate(), request.force());
        return ApiResponse.ok(result);
    }
}
