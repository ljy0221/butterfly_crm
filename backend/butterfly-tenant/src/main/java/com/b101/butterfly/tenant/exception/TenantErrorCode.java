package com.b101.butterfly.tenant.exception;

import com.b101.butterfly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TenantErrorCode implements ErrorCode {

    // ── 프로젝트 / 멤버 ────────────────────────────────────
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND,              "PROJ_002", "존재하지 않는 프로젝트입니다"),
    PROJECT_HAS_ACTIVE_CAMPAIGN(HttpStatus.CONFLICT,     "PROJ_003", "진행 중인 캠페인이 있어 삭제할 수 없습니다"),
    PROJECT_ACCESS_DENIED(HttpStatus.FORBIDDEN,          "PROJ_004", "접근 권한이 없습니다"),
    API_KEY_ALREADY_EXISTS(HttpStatus.CONFLICT,          "PROJ_005", "이미 발급된 키가 존재합니다. 재발급을 이용해주세요"),
    MEMBER_NOT_IN_PROJECT(HttpStatus.NOT_FOUND,          "PROJ_006", "프로젝트에 소속되지 않은 멤버입니다"),
    CANNOT_DELETE_OWNER(HttpStatus.CONFLICT,             "PROJ_007", "Owner는 삭제할 수 없습니다"),
    OWNER_TRANSFER_REQUIRED(HttpStatus.CONFLICT,         "PROJ_008", "Owner 권한 이전은 별도 절차가 필요합니다"),

    // ── 신청서 ─────────────────────────────────────────────
    INVALID_BUSINESS_NO_FORMAT(HttpStatus.BAD_REQUEST,   "APPLY_001", "사업자 번호 형식이 올바르지 않습니다"),
    DUPLICATE_BUSINESS_NO(HttpStatus.CONFLICT,           "APPLY_002", "이미 등록된 사업자 번호입니다"),
    APPLICATION_ALREADY_PROCESSED(HttpStatus.CONFLICT,   "APPLY_003", "이미 처리된 신청서입니다"),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND,          "APPLY_004", "존재하지 않는 신청서입니다"),
    REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST,       "APPLY_005", "반려 사유를 입력해야 합니다");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String errorMessage;

    @Override public String getCode()          { return errorCode;    }
    @Override public String getMessage()       { return errorMessage; }
    @Override public HttpStatus getHttpStatus() { return httpStatus;  }
}
