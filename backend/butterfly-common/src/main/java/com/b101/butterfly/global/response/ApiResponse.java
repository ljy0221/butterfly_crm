package com.b101.butterfly.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 공통 API 응답 포맷 (backend/docs/api/00-common.md 규약).
 *
 * <pre>
 * 성공: { "success": true,  "data": ..., "error": null }
 * 실패: { "success": false, "data": null, "error": { "code": ..., "message": ... } }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> fail(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorResponse(code, message));
    }
}
