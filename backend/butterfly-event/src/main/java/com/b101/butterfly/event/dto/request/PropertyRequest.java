package com.b101.butterfly.event.dto.request;

import com.b101.butterfly.event.entity.FieldType;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 스키마 필드 요청 element.
 *
 * <p>frontend {@code PropertyRecord} 와 1:1. type 은 lowercase 문자열로 받아 {@link FieldType} enum 으로
 * 변환한다 — {@code "datetime"} 은 {@link FieldType#DATE}.
 *
 * <p>{@code example} 컬럼이 entity 에 없어 현재 무시 (수집만 받고 저장 안 함).
 */
public record PropertyRequest(
        @NotBlank(message = "key 는 필수입니다.")
        @Size(max = 100, message = "key 는 100자 이하여야 합니다.")
        String key,

        @NotBlank(message = "type 은 필수입니다.")
        String type,

        boolean required,

        String example) {

    /** lowercase 문자열을 {@link FieldType} 으로 변환. 미지원 값은 {@link EventErrorCode#INVALID_PROPERTY_TYPE}. */
    public FieldType toFieldType() {
        return switch (type.toLowerCase()) {
            case "string" -> FieldType.STRING;
            case "number" -> FieldType.NUMBER;
            case "boolean" -> FieldType.BOOLEAN;
            case "datetime" -> FieldType.DATE;
            case "object" -> FieldType.OBJECT;
            default -> throw new BusinessException(EventErrorCode.INVALID_PROPERTY_TYPE);
        };
    }
}
