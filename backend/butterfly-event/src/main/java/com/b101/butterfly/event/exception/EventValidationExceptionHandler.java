package com.b101.butterfly.event.exception;

import com.b101.butterfly.global.error.CommonErrorCode;
import com.b101.butterfly.global.error.ErrorCode;
import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.ErrorResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.b101.butterfly.event")
@Profile("api")
public class EventValidationExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        ErrorCode ec = CommonErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.fail(new ErrorResponse(ec.getCode(), ec.getMessage())));
    }
}
