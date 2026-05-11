package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.request.CreateCategoryRequest;
import com.b101.butterfly.event.dto.request.PropertyRequest;
import com.b101.butterfly.event.dto.request.UpdateSchemaRequest;
import com.b101.butterfly.event.dto.response.DeleteCategoryResponse;
import com.b101.butterfly.event.dto.response.EventCategoryDetailResponse;
import com.b101.butterfly.event.dto.response.EventCategorySummaryResponse;
import com.b101.butterfly.event.dto.response.PropertyResponse;
import com.b101.butterfly.event.dto.response.RecentSampleResponse;
import com.b101.butterfly.event.entity.Event;
import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.EventCategoryType;
import com.b101.butterfly.event.entity.EventSchema;
import com.b101.butterfly.event.entity.EventSchemaField;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.event.repository.EventRepository;
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
import java.util.Optional;

/**
 * 카테고리 + 스키마 CRUD 서비스. frontend {@code /api/v1/projects/{}/events/categories} 7 종 endpoint 를
 * 통합 담당한다.
 *
 * <p>schema 관리는 {@link EventSchemaService} 가 이미 보유 (categoryId 기반, /event-categories/{id}/schemas
 * 경로). 본 서비스는 frontend 가 기대하는 categoryName 기반 + /events/categories 경로의 대응 + properties
 * 변환을 담당한다. 동일 schema 테이블을 공유하므로 PUT schema 는 새 version row 를 생성한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventCategoryService {

    private final EventCategoryRepository categoryRepository;
    private final EventSchemaRepository schemaRepository;
    private final EventSchemaFieldRepository fieldRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    /** 프로젝트 카테고리 LIST — frontend dropdown 채움용. */
    public List<EventCategorySummaryResponse> list(Long projectId) {
        List<EventCategory> categories = categoryRepository.findAllByProjectIdOrderByNameAsc(projectId);
        return categories.stream()
                .map(c -> EventCategorySummaryResponse.of(c, countCurrentFields(c.getId())))
                .toList();
    }

    /** categoryName 기반 단건 상세. */
    public EventCategoryDetailResponse get(Long projectId, String categoryName) {
        EventCategory category = findByName(projectId, categoryName);
        return EventCategoryDetailResponse.of(category);
    }

    /** 현재(is_current=true) 스키마의 필드 리스트. 스키마 미등록이면 빈 리스트. */
    public List<PropertyResponse> getSchema(Long projectId, String categoryName) {
        EventCategory category = findByName(projectId, categoryName);
        return findCurrentFields(category.getId()).stream()
                .map(PropertyResponse::of)
                .toList();
    }

    /** 카테고리 생성 + (선택) 초기 schema 생성. CUSTOM 타입으로 고정. */
    @Transactional
    public EventCategoryDetailResponse create(Long projectId, Long createdBy, CreateCategoryRequest request) {
        if (categoryRepository.existsByProjectIdAndName(projectId, request.categoryName())) {
            throw new BusinessException(EventErrorCode.CATEGORY_ALREADY_EXISTS);
        }
        EventCategory saved = categoryRepository.save(EventCategory.builder()
                .projectId(projectId)
                .name(request.categoryName())
                .type(EventCategoryType.CUSTOM)
                .description(request.resolveDescription())
                .build());

        List<PropertyRequest> initialProperties = request.properties();
        if (initialProperties != null && !initialProperties.isEmpty()) {
            createSchemaVersion(projectId, saved.getId(), initialProperties, createdBy, 1);
        }
        return EventCategoryDetailResponse.of(saved);
    }

    /** 스키마 업데이트 — 매 호출 새 version row 생성, 기존 row 의 is_current 비활성. */
    @Transactional
    public List<PropertyResponse> updateSchema(Long projectId, String categoryName,
                                               Long createdBy, UpdateSchemaRequest request) {
        EventCategory category = findByName(projectId, categoryName);

        int nextVersion = schemaRepository
                .findByCategoryIdAndIsCurrentTrue(category.getId())
                .map(s -> s.getVersion() + 1)
                .orElse(1);

        schemaRepository.deactivateCurrentByCategoryId(category.getId());

        EventSchema newSchema = createSchemaVersion(
                projectId, category.getId(), request.properties(), createdBy, nextVersion);
        return fieldRepository.findBySchemaId(newSchema.getId()).stream()
                .map(PropertyResponse::of)
                .toList();
    }

    /** 카테고리 삭제. DEFAULT(BUILTIN) 타입은 보호 — 삭제 거절. */
    @Transactional
    public DeleteCategoryResponse delete(Long projectId, Long categoryId) {
        EventCategory category = categoryRepository.findByIdAndProjectId(categoryId, projectId)
                .orElseThrow(() -> new BusinessException(EventErrorCode.CATEGORY_NOT_FOUND));
        if (category.getType() == EventCategoryType.DEFAULT) {
            throw new BusinessException(EventErrorCode.CATEGORY_BUILTIN_PROTECTED);
        }
        categoryRepository.delete(category);
        return new DeleteCategoryResponse(categoryId);
    }

    /** 최근 1건 샘플. 발생 이력 없으면 null. */
    public RecentSampleResponse getRecentSample(Long projectId, String categoryName) {
        EventCategory category = findByName(projectId, categoryName);
        Optional<Event> latest = eventRepository
                .findTopByProjectIdAndCategoryIdOrderByOccurredAtDesc(projectId, category.getId());
        return latest.map(e -> RecentSampleResponse.of(e, objectMapper)).orElse(null);
    }

    // ── private helpers ─────────────────────────────────────────────────────

    private EventCategory findByName(Long projectId, String categoryName) {
        return categoryRepository.findByProjectIdAndName(projectId, categoryName)
                .orElseThrow(() -> new BusinessException(EventErrorCode.CATEGORY_NOT_FOUND));
    }

    private int countCurrentFields(Long categoryId) {
        return findCurrentFields(categoryId).size();
    }

    private List<EventSchemaField> findCurrentFields(Long categoryId) {
        return schemaRepository.findByCategoryIdAndIsCurrentTrue(categoryId)
                .map(s -> fieldRepository.findBySchemaId(s.getId()))
                .orElse(List.of());
    }

    private EventSchema createSchemaVersion(Long projectId, Long categoryId,
                                            List<PropertyRequest> properties,
                                            Long createdBy, int version) {
        EventSchema schema = schemaRepository.save(EventSchema.builder()
                .projectId(projectId)
                .categoryId(categoryId)
                .version(version)
                .schemaDef(serialize(properties))
                .effectiveFrom(LocalDateTime.now())
                .isCurrent(true)
                .createdBy(createdBy != null ? createdBy : 0L)
                .build());

        List<EventSchemaField> fields = properties.stream()
                .map(p -> EventSchemaField.builder()
                        .schemaId(schema.getId())
                        .fieldName(p.key())
                        .fieldType(p.toFieldType())
                        .isRequired(p.required())
                        .isPromoted(false)
                        .previousNames(null)
                        .build())
                .toList();
        fieldRepository.saveAll(fields);
        return schema;
    }

    private String serialize(List<PropertyRequest> properties) {
        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw new BusinessException(EventErrorCode.INTERNAL_ERROR);
        }
    }
}
