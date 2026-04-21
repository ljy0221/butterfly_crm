package com.b101.butterfly.global.response;

/**
 * 공통 API 실패 응답의 error 필드 (backend/docs/api/00-common.md).
 *
 * <pre>
 * { "code": "AUTH_001", "message": "인증 토큰이 만료되었습니다." }
 * </pre>
 */
public record ErrorResponse(String code, String message) {
}
