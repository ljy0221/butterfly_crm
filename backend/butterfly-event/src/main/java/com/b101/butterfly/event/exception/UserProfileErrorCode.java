package com.b101.butterfly.event.exception;

import com.b101.butterfly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserProfileErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT_USER_001", "해당 사용자를 찾을 수 없습니다."),
    USER_NOT_IN_PROJECT(HttpStatus.NOT_FOUND, "EVENT_USER_002", "해당 사용자는 이 프로젝트에 속하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String errorMessage;

    @Override public String getCode()           { return errorCode;    }
    @Override public String getMessage()        { return errorMessage; }
    @Override public HttpStatus getHttpStatus() { return httpStatus;   }
}
