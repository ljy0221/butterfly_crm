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
    DEVICE_NOT_REGISTERED(HttpStatus.NOT_FOUND,          "DEVICE_NOT_REGISTERED",
            "등록되지 않은 디바이스입니다. SDK 초기화 시 디바이스 등록이 선행되어야 합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND,                 "USER_NOT_FOUND",
            "등록되지 않은 사용자입니다."),
    SCHEMA_NOT_FOUND(HttpStatus.NOT_FOUND,               "SCHEMA_NOT_FOUND",
            "해당 카테고리에 등록된 스키마가 없습니다."),
    SCHEMA_ALREADY_EXISTS(HttpStatus.CONFLICT,           "SCHEMA_ALREADY_EXISTS",
            "해당 카테고리에 이미 스키마가 존재합니다. 수정은 PUT /schemas 를 사용하세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,     "INTERNAL_ERROR",
            "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String errorMessage;

    @Override public String getCode()           { return errorCode;    }
    @Override public String getMessage()        { return errorMessage; }
    @Override public HttpStatus getHttpStatus() { return httpStatus;   }
}
