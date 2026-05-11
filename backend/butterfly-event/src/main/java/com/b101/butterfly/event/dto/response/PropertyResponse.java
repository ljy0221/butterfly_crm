package com.b101.butterfly.event.dto.response;

import com.b101.butterfly.event.entity.EventSchemaField;
import com.b101.butterfly.event.entity.FieldType;

/**
 * 스키마 필드 응답.
 *
 * <p>frontend {@code PropertyRecord} 와 1:1. type 은 lowercase 변환 + {@link FieldType#DATE} 는
 * {@code "datetime"} 으로 매핑한다 (이름 차이만, 의미 동일).
 *
 * <p>{@code example} 컬럼이 entity 에 없어 현재 항상 null. 후속 작업에서 컬럼 추가 시 채움.
 */
public record PropertyResponse(
        String key,
        String type,
        boolean required,
        String example) {

    public static PropertyResponse of(EventSchemaField field) {
        return new PropertyResponse(
                field.getFieldName(),
                toFrontendType(field.getFieldType()),
                field.isRequired(),
                null);
    }

    private static String toFrontendType(FieldType fieldType) {
        return switch (fieldType) {
            case STRING -> "string";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case DATE -> "datetime";
            case OBJECT -> "object";
        };
    }
}
