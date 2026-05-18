package com.b101.butterfly.segment.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;

@Converter
public class VariablesJsonConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> TYPE =
            new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("세그먼트 멤버 개인화 변수 JSON 직렬화 실패", e);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Map.of();
        }
        try {
            return Map.copyOf(OBJECT_MAPPER.readValue(dbData, TYPE));
        } catch (Exception e) {
            throw new IllegalArgumentException("세그먼트 멤버 개인화 변수 JSON 역직렬화 실패", e);
        }
    }
}
