package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.request.EventSchemaCreateRequest;
import com.b101.butterfly.event.dto.response.EventSchemaResponse;
import com.b101.butterfly.event.entity.EventSchema;
import com.b101.butterfly.event.entity.EventSchemaField;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.event.repository.EventSchemaFieldRepository;
import com.b101.butterfly.event.repository.EventSchemaRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventSchemaService {

    private final EventSchemaRepository schemaRepository;
    private final EventSchemaFieldRepository fieldRepository;
    private final EventCategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public EventSchemaResponse createSchema(Long projectId, Long categoryId,
                                            Long createdBy,
                                            EventSchemaCreateRequest request) {
        categoryRepository.findById(categoryId)
                .filter(c -> c.getProjectId().equals(projectId))
                .orElseThrow(() -> new BusinessException(EventErrorCode.CATEGORY_NOT_FOUND));

        if (schemaRepository.existsByCategoryId(categoryId)) {
            throw new BusinessException(EventErrorCode.SCHEMA_ALREADY_EXISTS);
        }

        EventSchema schema = schemaRepository.save(EventSchema.builder()
                .projectId(projectId)
                .categoryId(categoryId)
                .version(1)
                .schemaDef(serializeFields(request.fields()))
                .effectiveFrom(LocalDateTime.now())
                .isCurrent(true)
                .createdBy(createdBy)
                .build());

        List<EventSchemaField> savedFields = saveFields(schema.getId(), request.fields());

        return EventSchemaResponse.of(schema, savedFields);
    }

    @Transactional
    public EventSchemaResponse updateSchema(Long projectId, Long categoryId,
                                            Long createdBy,
                                            EventSchemaCreateRequest request) {
        categoryRepository.findById(categoryId)
                .filter(c -> c.getProjectId().equals(projectId))
                .orElseThrow(() -> new BusinessException(EventErrorCode.CATEGORY_NOT_FOUND));

        EventSchema current = schemaRepository.findByCategoryIdAndIsCurrentTrue(categoryId)
                .orElseThrow(() -> new BusinessException(EventErrorCode.SCHEMA_NOT_FOUND));

        schemaRepository.deactivateCurrentByCategoryId(categoryId);

        EventSchema next = schemaRepository.save(EventSchema.builder()
                .projectId(projectId)
                .categoryId(categoryId)
                .version(current.getVersion() + 1)
                .schemaDef(serializeFields(request.fields()))
                .effectiveFrom(LocalDateTime.now())
                .isCurrent(true)
                .createdBy(createdBy)
                .build());

        List<EventSchemaField> savedFields = saveFields(next.getId(), request.fields());

        return EventSchemaResponse.of(next, savedFields);
    }

    public EventSchemaResponse getCurrentSchema(Long projectId, Long categoryId) {
        categoryRepository.findById(categoryId)
                .filter(c -> c.getProjectId().equals(projectId))
                .orElseThrow(() -> new BusinessException(EventErrorCode.CATEGORY_NOT_FOUND));

        EventSchema schema = schemaRepository.findByCategoryIdAndIsCurrentTrue(categoryId)
                .orElseThrow(() -> new BusinessException(EventErrorCode.SCHEMA_NOT_FOUND));

        List<EventSchemaField> fields = fieldRepository.findBySchemaId(schema.getId());
        return EventSchemaResponse.of(schema, fields);
    }

    public List<EventSchemaResponse> listSchemas(Long projectId, Long categoryId) {
        categoryRepository.findById(categoryId)
                .filter(c -> c.getProjectId().equals(projectId))
                .orElseThrow(() -> new BusinessException(EventErrorCode.CATEGORY_NOT_FOUND));

        List<EventSchema> schemas = schemaRepository
                .findByProjectIdAndCategoryIdOrderByVersionDesc(projectId, categoryId);

        return schemas.stream()
                .map(s -> EventSchemaResponse.of(s, fieldRepository.findBySchemaId(s.getId())))
                .toList();
    }

    private List<EventSchemaField> saveFields(Long schemaId,
                                              List<EventSchemaCreateRequest.SchemaFieldRequest> fieldRequests) {
        List<EventSchemaField> fields = fieldRequests.stream()
                .map(f -> EventSchemaField.builder()
                        .schemaId(schemaId)
                        .fieldName(f.fieldName())
                        .fieldType(f.fieldType())
                        .isRequired(f.isRequired())
                        .isPromoted(f.isPromoted())
                        .previousNames(null)
                        .build())
                .toList();
        return fieldRepository.saveAll(fields);
    }

    private String serializeFields(List<EventSchemaCreateRequest.SchemaFieldRequest> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new BusinessException(EventErrorCode.INTERNAL_ERROR);
        }
    }
}
