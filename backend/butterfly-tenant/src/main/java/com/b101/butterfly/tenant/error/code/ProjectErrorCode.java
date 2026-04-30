package com.b101.butterfly.tenant.error.code;

import com.b101.butterfly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProjectErrorCode implements ErrorCode {
    PROJECT_NOT_FOUND("PROJECT_001", "존재하지 않는 프로젝트입니다.", HttpStatus.NOT_FOUND),
    ;


    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
