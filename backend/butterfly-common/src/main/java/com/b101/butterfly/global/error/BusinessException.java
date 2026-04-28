package com.b101.butterfly.global.error;

/**
 * 비즈니스 예외. 도메인 서비스에서 {@link ErrorCode}와 함께 던진다.
 *
 * <p>전역 핸들러가 이 예외를 잡아 {@code ApiResponse.fail(error)}와 HTTP 상태 코드로 변환한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
