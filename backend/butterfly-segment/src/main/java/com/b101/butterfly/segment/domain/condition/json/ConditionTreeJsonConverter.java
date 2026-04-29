package com.b101.butterfly.segment.domain.condition.json;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;

@Converter
public class ConditionTreeJsonConverter implements AttributeConverter<GroupNode, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new ConditionTreeJsonModule())
            .defaultPropertyInclusion(
                    JsonInclude.Value.construct(
                            JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Override
    public String convertToDatabaseColumn(GroupNode tree) {
        if (tree == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(tree);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize condition tree", e);
        }
    }

    @Override
    public GroupNode convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, GroupNode.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize condition tree", e);
        }
    }
}
