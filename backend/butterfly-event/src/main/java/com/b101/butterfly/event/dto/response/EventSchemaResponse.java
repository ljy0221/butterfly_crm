package com.b101.butterfly.event.dto.response;

import com.b101.butterfly.event.entity.EventSchema;
import com.b101.butterfly.event.entity.EventSchemaField;
import com.b101.butterfly.event.entity.FieldType;

import java.time.LocalDateTime;
import java.util.List;

public record EventSchemaResponse(
        Long schemaId,
        Long categoryId,
        Integer version,
        boolean isCurrent,
        LocalDateTime effectiveFrom,
        List<SchemaFieldResponse> fields,
        LocalDateTime createdAt
) {

    public record SchemaFieldResponse(
            Long fieldId,
            String fieldName,
            FieldType fieldType,
            boolean isRequired,
            boolean isPromoted
    ) {
        public static SchemaFieldResponse from(EventSchemaField f) {
            return new SchemaFieldResponse(
                    f.getId(), f.getFieldName(), f.getFieldType(),
                    f.isRequired(), f.isPromoted()
            );
        }
    }

    public static EventSchemaResponse of(EventSchema schema, List<EventSchemaField> fields) {
        return new EventSchemaResponse(
                schema.getId(),
                schema.getCategoryId(),
                schema.getVersion(),
                schema.isCurrent(),
                schema.getEffectiveFrom(),
                fields.stream().map(SchemaFieldResponse::from).toList(),
                schema.getCreatedAt()
        );
    }
}
