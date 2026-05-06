package com.b101.butterfly.event.dto.request;

import com.b101.butterfly.event.entity.FieldType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EventSchemaCreateRequest(

        @NotEmpty
        @Valid
        List<SchemaFieldRequest> fields

) {
    public record SchemaFieldRequest(

            @NotBlank
            @Size(max = 100)
            String fieldName,

            @NotNull
            FieldType fieldType,

            boolean isRequired,
            boolean isPromoted
    ) {}
}
