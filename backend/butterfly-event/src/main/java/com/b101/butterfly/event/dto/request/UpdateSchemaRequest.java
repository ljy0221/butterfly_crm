package com.b101.butterfly.event.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 스키마 업데이트 요청. 빈 리스트면 schema 의 fields 가 모두 비워진다 (= schema 제거 effect).
 *
 * <p>매 호출 시 새 schema_version row 가 생성되고 기존 row 의 {@code is_current} 가 false 로 전환된다.
 */
public record UpdateSchemaRequest(
        @NotNull(message = "properties 는 필수입니다 (빈 배열은 허용).")
        @Valid
        List<PropertyRequest> properties) {
}
