package com.b101.butterfly.global.error;

import org.springframework.http.HttpStatus;

/**
 * 전 도메인 공통 에러 코드. 도메인 특화 에러는 각 도메인의 자체 enum에 정의한다.
 */
public enum CommonErrorCode implements ErrorCode {

    INVALID_REQUEST("COMMON_001", "요청이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("COMMON_002", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("COMMON_003", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    NOT_FOUND("COMMON_004", "리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CONFLICT("COMMON_005", "리소스 상태가 충돌합니다.", HttpStatus.CONFLICT),
    METHOD_NOT_ALLOWED("COMMON_006", "허용되지 않은 HTTP 메서드입니다.", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("COMMON_007", "지원되지 않는 미디어 타입입니다.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_ERROR("COMMON_999", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    CommonErrorCode(String code, String message, HttpStatus httpStatus) {
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
