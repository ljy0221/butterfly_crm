package com.b101.butterfly.global.error;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 전역 예외 핸들러. butterfly-api의 컴포넌트 스캔에 포함되어 자동 등록된다.
 *
 * <p>처리 대상:
 * <ul>
 *   <li>{@link BusinessException} — 도메인 예외, ErrorCode 기반 매핑</li>
 *   <li>{@link MethodArgumentNotValidException} — {@code @Valid @RequestBody} 검증 실패 (400)</li>
 *   <li>{@link ConstraintViolationException} — {@code @Validated} 메서드 파라미터 검증 실패 (400)</li>
 *   <li>{@link HttpMessageNotReadableException} — JSON 파싱 실패/잘못된 타입 (400)</li>
 *   <li>{@link MethodArgumentTypeMismatchException} — PathVariable/RequestParam 타입 불일치 (400)</li>
 *   <li>{@link MissingServletRequestParameterException} — 필수 RequestParam 누락 (400)</li>
 *   <li>{@link HttpRequestMethodNotSupportedException} — 잘못된 HTTP 메서드 (405)</li>
 *   <li>{@link HttpMediaTypeNotSupportedException} — 잘못된 Content-Type (415)</li>
 *   <li>{@link Exception} — 그 외 (500)</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        log.warn("BusinessException: code={} message={}", ec.getCode(), e.getMessage());
        return buildResponse(ec, ec.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse(CommonErrorCode.INVALID_REQUEST.getMessage());
        log.warn("Validation failed: {}", detail);
        return buildResponse(CommonErrorCode.INVALID_REQUEST, detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .orElse(CommonErrorCode.INVALID_REQUEST.getMessage());
        log.warn("Constraint violation: {}", detail);
        return buildResponse(CommonErrorCode.INVALID_REQUEST, detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("Malformed JSON request: {}", e.getMostSpecificCause().getMessage());
        return buildResponse(CommonErrorCode.INVALID_REQUEST, "요청 본문이 올바르지 않습니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String required = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
        String detail = String.format("파라미터 '%s'의 타입이 올바르지 않습니다 (required=%s).",
                e.getName(), required);
        log.warn("Type mismatch: {}", detail);
        return buildResponse(CommonErrorCode.INVALID_REQUEST, detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        String detail = String.format("필수 파라미터 '%s'(%s)가 누락되었습니다.",
                e.getParameterName(), e.getParameterType());
        log.warn("Missing parameter: {}", detail);
        return buildResponse(CommonErrorCode.INVALID_REQUEST, detail);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMethod());
        return buildResponse(CommonErrorCode.METHOD_NOT_ALLOWED,
                CommonErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported media type: {}", e.getContentType());
        return buildResponse(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE,
                CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return buildResponse(CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(ErrorCode ec, String message) {
        HttpStatus status = ec.getHttpStatus();
        return ResponseEntity
                .status(status)
                .body(ApiResponse.fail(new ErrorResponse(ec.getCode(), message)));
    }
}
