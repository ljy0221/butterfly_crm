package com.b101.butterfly.campaign.messagetemplate.domain;

import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter(autoApply = false)
public class RequiredVariablesConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };
    private static final String EMPTY_JSON_ARRAY = "[]";

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return EMPTY_JSON_ARRAY;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    MessageTemplateErrorCode.TEMPLATE_REQUIRED_VARIABLES_INVALID);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(dbData, LIST_TYPE);
            return List.copyOf(parsed);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    MessageTemplateErrorCode.TEMPLATE_REQUIRED_VARIABLES_INVALID);
        }
    }
}
