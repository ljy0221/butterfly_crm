package com.b101.butterfly.segment.exception;

import com.b101.butterfly.global.error.CommonErrorCode;
import com.b101.butterfly.global.error.ErrorCode;
import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.ErrorResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * segment 컨트롤러 한정 Bean Validation 핸들러.
 *
 * <p>{@code @ValidConditionTree} 위반 → {@link SegmentErrorCode#SEGMENT_400_INVALID_CONDITION_TREE}.
 * 그 외 일반 제약 위반 → {@link CommonErrorCode#INVALID_REQUEST}.
 *
 * <p>{@code butterfly-common.GlobalExceptionHandler}는 BusinessException과 Exception만 잡으므로,
 * 도메인 한정 advice로 분리해 다른 모듈 영향 없이 처리한다.
 */
@RestControllerAdvice(basePackages = "com.b101.butterfly.segment")
@Profile("api")
public class SegmentValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        boolean hasConditionTreeViolation =
                e.getBindingResult().getFieldErrors().stream()
                        .anyMatch(fe -> "ValidConditionTree".equals(fe.getCode()));

        ErrorCode errorCode =
                hasConditionTreeViolation
                        ? SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE
                        : CommonErrorCode.INVALID_REQUEST;

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(
                        new ErrorResponse(errorCode.getCode(), errorCode.getMessage())));
    }
}
