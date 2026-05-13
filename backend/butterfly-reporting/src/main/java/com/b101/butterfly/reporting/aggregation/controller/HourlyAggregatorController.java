package com.b101.butterfly.reporting.aggregation.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.reporting.aggregation.dto.HourlyAggregateRequest;
import com.b101.butterfly.reporting.aggregation.dto.HourlyAggregateResponse;
import com.b101.butterfly.reporting.aggregation.service.HourlyAggregator;
import com.b101.butterfly.reporting.notification.controller.InternalTokenGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hourly 집계 수동 트리거 엔드포인트 (API #28 — butterfly_api_spec_v2.md).
 *
 * <p>운영 도구나 자동 배치 외 단발 재처리에 사용한다. 외부 노출 차단을 위해
 * {@link InternalTokenGuard}가 `X-Internal-Token` 헤더를 검증한다 — 운영 게이트웨이/네트워크
 * 정책이 1차 방어선, 본 가드가 2차.
 */
@RestController
@Profile("api")
@RequestMapping("/internal/stats/aggregate/hourly")
@RequiredArgsConstructor
public class HourlyAggregatorController {

    private final HourlyAggregator hourlyAggregator;
    private final InternalTokenGuard internalTokenGuard;

    @PostMapping
    public ApiResponse<HourlyAggregateResponse> trigger(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @Valid @RequestBody HourlyAggregateRequest request
    ) {
        internalTokenGuard.verify(internalToken);

        long start = System.currentTimeMillis();
        int processed = hourlyAggregator.aggregate(request.bucketHour(), request.force());
        long elapsed = System.currentTimeMillis() - start;

        return ApiResponse.ok(new HourlyAggregateResponse(processed, elapsed));
    }
}
