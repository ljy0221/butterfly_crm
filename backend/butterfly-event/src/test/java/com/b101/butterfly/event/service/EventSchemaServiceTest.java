package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.request.EventSchemaCreateRequest;
import com.b101.butterfly.event.dto.response.EventSchemaResponse;
import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.EventCategoryType;
import com.b101.butterfly.event.entity.EventSchema;
import com.b101.butterfly.event.entity.EventSchemaField;
import com.b101.butterfly.event.entity.FieldType;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.event.repository.EventSchemaFieldRepository;
import com.b101.butterfly.event.repository.EventSchemaRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventSchemaServiceTest {

    @Mock private EventSchemaRepository schemaRepository;
    @Mock private EventSchemaFieldRepository fieldRepository;
    @Mock private EventCategoryRepository categoryRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private EventSchemaService service;

    private static final Long PROJECT_ID = 1L;
    private static final Long CATEGORY_ID = 10L;
    private static final Long CREATOR_ID = 99L;

    private EventCategory category() {
        EventCategory c = EventCategory.builder()
                .projectId(PROJECT_ID)
                .name("purchase_complete")
                .type(EventCategoryType.CUSTOM)
                .description(null)
                .build();
        ReflectionTestUtils.setField(c, "id", CATEGORY_ID);
        return c;
    }

    private EventSchema schema(int version, boolean isCurrent) {
        EventSchema s = EventSchema.builder()
                .projectId(PROJECT_ID)
                .categoryId(CATEGORY_ID)
                .version(version)
                .schemaDef("[]")
                .effectiveFrom(LocalDateTime.now())
                .isCurrent(isCurrent)
                .createdBy(CREATOR_ID)
                .build();
        ReflectionTestUtils.setField(s, "id", (long) version * 100);
        ReflectionTestUtils.setField(s, "createdAt", LocalDateTime.now());
        return s;
    }

    private EventSchemaField field(Long schemaId, String name) {
        EventSchemaField f = EventSchemaField.builder()
                .schemaId(schemaId)
                .fieldName(name)
                .fieldType(FieldType.STRING)
                .isRequired(false)
                .isPromoted(false)
                .previousNames(null)
                .build();
        ReflectionTestUtils.setField(f, "id", (long) Math.abs(name.hashCode()));
        ReflectionTestUtils.setField(f, "createdAt", LocalDateTime.now());
        return f;
    }

    private EventSchemaCreateRequest request(String... fieldNames) {
        List<EventSchemaCreateRequest.SchemaFieldRequest> fields = Arrays.stream(fieldNames)
                .map(n -> new EventSchemaCreateRequest.SchemaFieldRequest(n, FieldType.STRING, false, false))
                .toList();
        return new EventSchemaCreateRequest(fields);
    }

    // ─── createSchema ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("createSchema — 정상 흐름: version=1, is_current=true 스키마 생성")
    void createSchema_happyPath_returnsV1Response() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(schemaRepository.existsByCategoryId(CATEGORY_ID)).thenReturn(false);

        EventSchema saved = schema(1, true);
        when(schemaRepository.save(any())).thenReturn(saved);
        when(fieldRepository.saveAll(any())).thenReturn(List.of(field(saved.getId(), "amount")));

        EventSchemaResponse resp = service.createSchema(PROJECT_ID, CATEGORY_ID, CREATOR_ID, request("amount"));

        assertThat(resp.version()).isEqualTo(1);
        assertThat(resp.isCurrent()).isTrue();
        assertThat(resp.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(resp.fields()).hasSize(1);
        assertThat(resp.fields().get(0).fieldName()).isEqualTo("amount");
    }

    @Test
    @DisplayName("createSchema — 카테고리 없음 → CATEGORY_NOT_FOUND, 스키마 저장 안 함")
    void createSchema_categoryNotFound_throwsAndSkipsSave() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSchema(PROJECT_ID, CATEGORY_ID, CREATOR_ID, request("amount")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.CATEGORY_NOT_FOUND);

        verify(schemaRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSchema — 다른 프로젝트의 카테고리 → CATEGORY_NOT_FOUND")
    void createSchema_categoryBelongsToDifferentProject_throwsNotFound() {
        EventCategory other = EventCategory.builder()
                .projectId(999L)
                .name("purchase_complete")
                .type(EventCategoryType.CUSTOM)
                .description(null)
                .build();
        ReflectionTestUtils.setField(other, "id", CATEGORY_ID);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.createSchema(PROJECT_ID, CATEGORY_ID, CREATOR_ID, request("amount")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("createSchema — 카테고리에 스키마 이미 존재 → SCHEMA_ALREADY_EXISTS")
    void createSchema_alreadyExists_throwsConflict() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(schemaRepository.existsByCategoryId(CATEGORY_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.createSchema(PROJECT_ID, CATEGORY_ID, CREATOR_ID, request("amount")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.SCHEMA_ALREADY_EXISTS);

        verify(schemaRepository, never()).save(any());
    }

    // ─── updateSchema ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateSchema — 정상 흐름: 기존 비활성화 후 version+1 생성")
    void updateSchema_happyPath_deactivatesOldAndCreatesNext() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));

        EventSchema current = schema(1, true);
        when(schemaRepository.findByCategoryIdAndIsCurrentTrue(CATEGORY_ID)).thenReturn(Optional.of(current));

        EventSchema next = schema(2, true);
        when(schemaRepository.save(any())).thenReturn(next);
        when(fieldRepository.saveAll(any())).thenReturn(List.of(field(next.getId(), "price")));

        EventSchemaResponse resp = service.updateSchema(PROJECT_ID, CATEGORY_ID, CREATOR_ID, request("price"));

        verify(schemaRepository).deactivateCurrentByCategoryId(CATEGORY_ID);
        assertThat(resp.version()).isEqualTo(2);
        assertThat(resp.isCurrent()).isTrue();
    }

    @Test
    @DisplayName("updateSchema — 현행 스키마 없음 → SCHEMA_NOT_FOUND, 저장 안 함")
    void updateSchema_noCurrentSchema_throwsNotFound() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(schemaRepository.findByCategoryIdAndIsCurrentTrue(CATEGORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSchema(PROJECT_ID, CATEGORY_ID, CREATOR_ID, request("price")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.SCHEMA_NOT_FOUND);

        verify(schemaRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateSchema — 카테고리 없음 → CATEGORY_NOT_FOUND")
    void updateSchema_categoryNotFound_throwsNotFound() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSchema(PROJECT_ID, CATEGORY_ID, CREATOR_ID, request("price")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.CATEGORY_NOT_FOUND);
    }

    // ─── getCurrentSchema ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentSchema — 현행 스키마와 필드 목록 반환")
    void getCurrentSchema_returnsCurrentSchemaWithFields() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        EventSchema current = schema(3, true);
        when(schemaRepository.findByCategoryIdAndIsCurrentTrue(CATEGORY_ID)).thenReturn(Optional.of(current));
        when(fieldRepository.findBySchemaId(current.getId())).thenReturn(
                List.of(field(current.getId(), "qty"), field(current.getId(), "price"))
        );

        EventSchemaResponse resp = service.getCurrentSchema(PROJECT_ID, CATEGORY_ID);

        assertThat(resp.version()).isEqualTo(3);
        assertThat(resp.isCurrent()).isTrue();
        assertThat(resp.fields()).hasSize(2);
    }

    @Test
    @DisplayName("getCurrentSchema — 스키마 없음 → SCHEMA_NOT_FOUND")
    void getCurrentSchema_noSchema_throwsNotFound() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(schemaRepository.findByCategoryIdAndIsCurrentTrue(CATEGORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentSchema(PROJECT_ID, CATEGORY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.SCHEMA_NOT_FOUND);
    }

    // ─── listSchemas ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listSchemas — 버전 내림차순으로 전체 이력 반환")
    void listSchemas_returnsAllVersionsInDescOrder() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));

        EventSchema v2 = schema(2, true);
        EventSchema v1 = schema(1, false);
        when(schemaRepository.findByProjectIdAndCategoryIdOrderByVersionDesc(PROJECT_ID, CATEGORY_ID))
                .thenReturn(List.of(v2, v1));
        when(fieldRepository.findBySchemaId(v2.getId())).thenReturn(List.of());
        when(fieldRepository.findBySchemaId(v1.getId())).thenReturn(List.of());

        List<EventSchemaResponse> resp = service.listSchemas(PROJECT_ID, CATEGORY_ID);

        assertThat(resp).hasSize(2);
        assertThat(resp.get(0).version()).isEqualTo(2);
        assertThat(resp.get(1).version()).isEqualTo(1);
        assertThat(resp.get(0).isCurrent()).isTrue();
        assertThat(resp.get(1).isCurrent()).isFalse();
    }

    @Test
    @DisplayName("listSchemas — 스키마 없는 카테고리 → 빈 목록 반환")
    void listSchemas_noSchemas_returnsEmptyList() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(schemaRepository.findByProjectIdAndCategoryIdOrderByVersionDesc(PROJECT_ID, CATEGORY_ID))
                .thenReturn(List.of());

        List<EventSchemaResponse> resp = service.listSchemas(PROJECT_ID, CATEGORY_ID);

        assertThat(resp).isEmpty();
    }
}
