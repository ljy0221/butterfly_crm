package com.b101.butterfly.delivery.exception;

import com.b101.butterfly.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DeliveryErrorCode implements ErrorCode {

    LOG_NOT_FOUND("DELIVERY_001", "발송 로그를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_STATUS_TRANSITION("DELIVERY_002", "유효하지 않은 상태 전이입니다.", HttpStatus.CONFLICT),
    FCM_SEND_FAILED("DELIVERY_003", "FCM 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    FCM_CREDENTIALS_NOT_FOUND("DELIVERY_004", "프로젝트의 FCM 자격증명이 설정되지 않았습니다.", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_CHANNEL("DELIVERY_005", "지원하지 않는 발송 채널입니다.", HttpStatus.BAD_REQUEST),
    CAMPAIGN_STATS_NOT_FOUND("DELIVERY_006", "캠페인 통계를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    EMPTY_TARGETS("DELIVERY_007", "발송 대상이 비어 있습니다.", HttpStatus.BAD_REQUEST),
    FCM_CREDENTIALS_INVALID("DELIVERY_008", "FCM 자격증명이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    EMAIL_SEND_FAILED("DELIVERY_009", "이메일 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    EMAIL_INVALID_ADDRESS("DELIVERY_010", "유효하지 않은 이메일 주소입니다.", HttpStatus.BAD_REQUEST),
    SMS_SEND_FAILED("DELIVERY_011", "SMS 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    SMS_INVALID_NUMBER("DELIVERY_012", "유효하지 않은 전화번호입니다.", HttpStatus.BAD_REQUEST),
    WEBHOOK_SEND_FAILED("DELIVERY_013", "Webhook 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    WEBHOOK_URL_INVALID("DELIVERY_014", "유효하지 않은 Webhook URL입니다.", HttpStatus.BAD_REQUEST),
    FCM_TOKEN_MISSING("DELIVERY_015", "Push token이 누락되었습니다.", HttpStatus.BAD_REQUEST),
    EMAIL_ADDRESS_MISSING("DELIVERY_016", "이메일 주���가 누락되었습니다.", HttpStatus.BAD_REQUEST),
    SMS_PHONE_MISSING("DELIVERY_017", "전화번호가 누락되었습���다.", HttpStatus.BAD_REQUEST),
    WEBHOOK_URL_MISSING("DELIVERY_018", "Webhook URL이 누락되었습니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    DeliveryErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
