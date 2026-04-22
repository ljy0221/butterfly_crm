package com.b101.butterfly.analytics.exception;

import com.b101.butterfly.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 분석 도메인 전용 에러 코드. prefix: {@code ANALYTICS_}.
 */
public enum AnalyticsErrorCode implements ErrorCode {

    CAMPAIGN_STATS_NOT_FOUND("ANALYTICS_001", "캠페인 통계를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_DATE_RANGE("ANALYTICS_002", "from은 to보다 이전이어야 합니다.", HttpStatus.BAD_REQUEST),
    DATE_RANGE_TOO_LARGE("ANALYTICS_003", "조회 기간이 너무 깁니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    AnalyticsErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
