package com.b101.butterfly.global.error;

import org.springframework.http.HttpStatus;

/**
 * 도메인별 에러 코드의 공통 계약.
 *
 * <p>각 도메인 모듈은 자신만의 enum을 정의해 이 인터페이스를 구현한다.
 * 코드 prefix로 도메인을 구분한다 (예: {@code AUTH_001}, {@code ANALYTICS_001}).
 *
 * <p>공통 에러는 {@link CommonErrorCode}에 정의한다.
 */
public interface ErrorCode {

    String code();

    String message();

    HttpStatus httpStatus();
}
