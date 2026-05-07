package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.EventSchema;
import com.b101.butterfly.event.entity.EventSchemaField;
import com.b101.butterfly.event.entity.FieldType;
import com.b101.butterfly.event.support.AbstractMySqlContainerTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventSchemaRepositoryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private EventSchemaRepository schemaRepository;
    @Autowired private EventSchemaFieldRepository fieldRepository;
    @Autowired private EntityManager em;

    private static final Long PROJECT_ID = 1L;
    private static final Long CATEGORY_ID = 10L;

    private EventSchema saveSchema(int version, boolean isCurrent) {
        EventSchema schema = EventSchema.builder()
                .projectId(PROJECT_ID)
                .categoryId(CATEGORY_ID)
                .version(version)
                .schemaDef("{\"fields\":[]}")
                .effectiveFrom(LocalDateTime.of(2026, 5, 6, 10, 0))
                .isCurrent(isCurrent)
                .createdBy(0L)
                .build();
        return schemaRepository.save(schema);
    }

    // ─── EventSchemaRepository ────────────────────────────────────────────────────

    @Test
    @DisplayName("save + findByCategoryIdAndIsCurrentTrue — is_current=true인 단건 반환")
    void findCurrent_returnsCurrentSchema() {
        saveSchema(1, false);
        saveSchema(2, true);
        em.flush();
        em.clear();

        Optional<EventSchema> result = schemaRepository.findByCategoryIdAndIsCurrentTrue(CATEGORY_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getVersion()).isEqualTo(2);
        assertThat(result.get().isCurrent()).isTrue();
    }

    @Test
    @DisplayName("findByCategoryIdAndIsCurrentTrue — is_current=true 없으면 empty")
    void findCurrent_noCurrentSchema_returnsEmpty() {
        saveSchema(1, false);
        em.flush();
        em.clear();

        Optional<EventSchema> result = schemaRepository.findByCategoryIdAndIsCurrentTrue(CATEGORY_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("existsByCategoryId — 스키마 없으면 false, 있으면 true")
    void existsByCategoryId_falseBeforeSaveTrueAfter() {
        assertThat(schemaRepository.existsByCategoryId(CATEGORY_ID)).isFalse();

        saveSchema(1, true);
        em.flush();
        em.clear();

        assertThat(schemaRepository.existsByCategoryId(CATEGORY_ID)).isTrue();
    }

    @Test
    @DisplayName("deactivateCurrentByCategoryId — 해당 카테고리 current를 false로 전환")
    void deactivateCurrent_setsIsCurrentFalse() {
        saveSchema(1, true);
        em.flush();
        em.clear();

        schemaRepository.deactivateCurrentByCategoryId(CATEGORY_ID);
        em.flush();
        em.clear();

        Optional<EventSchema> current = schemaRepository.findByCategoryIdAndIsCurrentTrue(CATEGORY_ID);
        assertThat(current).isEmpty();

        List<EventSchema> all = schemaRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).isCurrent()).isFalse();
    }

    @Test
    @DisplayName("deactivateCurrentByCategoryId — 다른 카테고리의 current는 영향 없음")
    void deactivateCurrent_doesNotAffectOtherCategories() {
        saveSchema(1, true);

        EventSchema otherCategory = EventSchema.builder()
                .projectId(PROJECT_ID)
                .categoryId(99L)
                .version(1)
                .schemaDef("{}")
                .effectiveFrom(LocalDateTime.now())
                .isCurrent(true)
                .createdBy(0L)
                .build();
        schemaRepository.save(otherCategory);
        em.flush();
        em.clear();

        schemaRepository.deactivateCurrentByCategoryId(CATEGORY_ID);
        em.flush();
        em.clear();

        Optional<EventSchema> other = schemaRepository.findByCategoryIdAndIsCurrentTrue(99L);
        assertThat(other).isPresent();
    }

    @Test
    @DisplayName("findByProjectIdAndCategoryIdOrderByVersionDesc — 버전 내림차순 반환")
    void findAllVersions_returnsSortedByVersionDesc() {
        saveSchema(1, false);
        saveSchema(3, false);
        saveSchema(2, true);
        em.flush();
        em.clear();

        List<EventSchema> schemas = schemaRepository
                .findByProjectIdAndCategoryIdOrderByVersionDesc(PROJECT_ID, CATEGORY_ID);

        assertThat(schemas).hasSize(3);
        assertThat(schemas.get(0).getVersion()).isEqualTo(3);
        assertThat(schemas.get(1).getVersion()).isEqualTo(2);
        assertThat(schemas.get(2).getVersion()).isEqualTo(1);
    }

    // ─── EventSchemaFieldRepository ──────────────────────────────────────────────

    @Test
    @DisplayName("findBySchemaId — 해당 schema_id에 속한 모든 필드 반환")
    void findFieldsBySchemaId_returnsAllFields() {
        EventSchema schema = saveSchema(1, true);
        em.flush();

        EventSchemaField f1 = EventSchemaField.builder()
                .schemaId(schema.getId())
                .fieldName("amount")
                .fieldType(FieldType.NUMBER)
                .isRequired(true)
                .isPromoted(false)
                .previousNames(null)
                .build();
        EventSchemaField f2 = EventSchemaField.builder()
                .schemaId(schema.getId())
                .fieldName("item")
                .fieldType(FieldType.STRING)
                .isRequired(false)
                .isPromoted(true)
                .previousNames(null)
                .build();
        fieldRepository.saveAll(List.of(f1, f2));
        em.flush();
        em.clear();

        List<EventSchemaField> fields = fieldRepository.findBySchemaId(schema.getId());

        assertThat(fields).hasSize(2)
                .extracting(EventSchemaField::getFieldName)
                .containsExactlyInAnyOrder("amount", "item");
    }

    @Test
    @DisplayName("findBySchemaId — 다른 schemaId의 필드는 조회되지 않음")
    void findFieldsBySchemaId_doesNotReturnOtherSchemaFields() {
        EventSchema schema1 = saveSchema(1, false);
        EventSchema schema2 = saveSchema(2, true);
        em.flush();

        fieldRepository.save(EventSchemaField.builder()
                .schemaId(schema1.getId()).fieldName("old_field")
                .fieldType(FieldType.STRING).isRequired(false).isPromoted(false).build());
        fieldRepository.save(EventSchemaField.builder()
                .schemaId(schema2.getId()).fieldName("new_field")
                .fieldType(FieldType.STRING).isRequired(false).isPromoted(false).build());
        em.flush();
        em.clear();

        List<EventSchemaField> fields = fieldRepository.findBySchemaId(schema2.getId());

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).getFieldName()).isEqualTo("new_field");
    }

    @Test
    @DisplayName("EventSchemaField — isRequired, isPromoted 값 정확히 저장/조회")
    void fieldBooleanColumns_persistedCorrectly() {
        EventSchema schema = saveSchema(1, true);
        em.flush();

        fieldRepository.save(EventSchemaField.builder()
                .schemaId(schema.getId())
                .fieldName("price")
                .fieldType(FieldType.NUMBER)
                .isRequired(true)
                .isPromoted(true)
                .previousNames(null)
                .build());
        em.flush();
        em.clear();

        EventSchemaField loaded = fieldRepository.findBySchemaId(schema.getId()).get(0);
        assertThat(loaded.isRequired()).isTrue();
        assertThat(loaded.isPromoted()).isTrue();
        assertThat(loaded.getFieldType()).isEqualTo(FieldType.NUMBER);
    }
}
