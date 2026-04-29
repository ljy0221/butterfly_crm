package com.b101.butterfly.auth.exception;

import com.b101.butterfly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    // ── 인증 ───────────────────────────────────────────────
    DUPLICATE_EMAIL(HttpStatus.CONFLICT,              "AUTH_001", "이미 사용 중인 이메일입니다"),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST,        "AUTH_002", "이메일 인증이 필요합니다"),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST,        "AUTH_003", "인증 코드가 만료되었습니다"),
    EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST,        "AUTH_004", "인증 코드가 올바르지 않습니다"),
    EMAIL_CODE_LIMIT(HttpStatus.TOO_MANY_REQUESTS,    "AUTH_005", "인증 코드 발송 횟수를 초과했습니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,      "AUTH_006", "이메일 또는 비밀번호가 올바르지 않습니다"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND,            "AUTH_008", "존재하지 않는 회원입니다"),
    CURRENT_PASSWORD_WRONG(HttpStatus.UNAUTHORIZED,   "AUTH_009", "현재 비밀번호가 올바르지 않습니다"),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST,             "AUTH_010", "현재 비밀번호와 동일합니다"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED,            "AUTH_011", "유효하지 않은 토큰입니다"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED,    "AUTH_012", "유효하지 않은 리프레시 토큰입니다"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,    "AUTH_013", "리프레시 토큰이 만료되었습니다"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN,               "AUTH_014", "접근 권한이 없습니다"),
    INVALID_COMPANY_AUTH_NO(HttpStatus.BAD_REQUEST,   "AUTH_015", "유효하지 않은 회사 인증번호입니다"),
    PASSWORD_NOT_SET(HttpStatus.FORBIDDEN,            "AUTH_016", "비밀번호 설정이 필요합니다"),
    OWNER_LINK_EXPIRED(HttpStatus.BAD_REQUEST,        "AUTH_017", "인증 링크가 만료되었습니다"),

    // ── 프로젝트 소유자 탈퇴 불가 (auth 계층에서 사용) ────────
    OWNER_CANNOT_WITHDRAW(HttpStatus.CONFLICT,        "PROJ_001", "프로젝트 소유자는 탈퇴할 수 없습니다"),

    // ── 시스템 ─────────────────────────────────────────────
    ENCRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SYS_001", "암호화 처리 중 오류가 발생했습니다");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String errorMessage;

    @Override public String getCode()          { return errorCode;    }
    @Override public String getMessage()       { return errorMessage; }
    @Override public HttpStatus getHttpStatus() { return httpStatus;  }
}
