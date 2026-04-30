package com.b101.butterfly.event.exception;

import com.b101.butterfly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EventErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND,             "CATEGORY_NOT_FOUND",
            "등록되지 않은 이벤트 카테고리입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,     "INTERNAL_ERROR",
            "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String errorMessage;

    @Override public String getCode()           { return errorCode;    }
    @Override public String getMessage()        { return errorMessage; }
    @Override public HttpStatus getHttpStatus() { return httpStatus;   }
}
