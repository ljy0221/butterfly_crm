package com.b101.butterfly.global.error;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 핸들러. butterfly-api의 컴포넌트 스캔에 포함되어 자동 등록된다.
 *
 * <p>Validation/인증 등의 스프링 표준 예외 처리는 각 도메인에서 필요 시 추가한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        log.warn("BusinessException: code={} message={}", ec.code(), e.getMessage());
        return ResponseEntity
                .status(ec.httpStatus())
                .body(ApiResponse.fail(new ErrorResponse(ec.code(), ec.message())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode ec = CommonErrorCode.INTERNAL_ERROR;
        return ResponseEntity
                .status(ec.httpStatus())
                .body(ApiResponse.fail(new ErrorResponse(ec.code(), ec.message())));
    }
}
