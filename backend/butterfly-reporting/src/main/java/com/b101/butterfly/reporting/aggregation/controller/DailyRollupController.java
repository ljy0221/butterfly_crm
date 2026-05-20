package com.b101.butterfly.reporting.aggregation.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.reporting.aggregation.dto.DailyRollupRequest;
import com.b101.butterfly.reporting.aggregation.dto.DailyRollupResponse;
import com.b101.butterfly.reporting.aggregation.service.DailyRollupService;
import com.b101.butterfly.reporting.notification.controller.InternalTokenGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Daily 롤업 수동 트리거 엔드포인트 (API #29 — butterfly_api_spec_v2.md).
 *
 * <p>스케줄러가 매일 새벽 1시 자동 실행하지만, 재처리·검증 목적으로 수동 호출할 수 있다.
 * targetDate가 null이면 전날을 기본값으로 사용하고, force=true면 기존 row 삭제 후 재집계한다.
 * {@link InternalTokenGuard}가 X-Internal-Token 헤더로 내부 호출 여부를 검증한다.
 */
@RestController
@Profile("api")
@RequestMapping("/internal/stats/rollup/daily")
@RequiredArgsConstructor
public class DailyRollupController {

    private final DailyRollupService dailyRollupService;
    private final InternalTokenGuard internalTokenGuard;

    @PostMapping
    public ApiResponse<DailyRollupResponse> trigger(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody DailyRollupRequest request
    ) {
        internalTokenGuard.verify(internalToken);

        LocalDate targetDate = request.targetDate() != null
                ? request.targetDate()
                : LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);

        long start = System.currentTimeMillis();
        int[] rows = dailyRollupService.rollup(targetDate, request.force());
        long elapsed = System.currentTimeMillis() - start;

        return ApiResponse.ok(new DailyRollupResponse(
                targetDate,
                rows[0], rows[1], rows[2], rows[3],
                elapsed
        ));
    }
}
