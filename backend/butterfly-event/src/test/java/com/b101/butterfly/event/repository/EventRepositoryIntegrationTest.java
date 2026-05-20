package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.Event;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventRepositoryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private EventRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertParentFixtures() {
        // 1. member (owner of project)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO members (member_id, project_id, email, name, phone, birth_date, role_type, member_status)
                VALUES (1, NULL, 'event-test@test.com', '이벤트테스트', '01012345678', '1990-01-01', 'OWNER', 'ACTIVE')
                """);
        // 2. projects (project_id=100, 999 used in tests)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO projects (project_id, name, business_no, company_auth_no, owner_id, plan)
                VALUES (100, 'event-project-100', '123-45-67890', 'COMP100', 1, 'FREE')
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO projects (project_id, name, business_no, company_auth_no, owner_id, plan)
                VALUES (999, 'event-project-999', '123-45-67999', 'COMP999', 1, 'FREE')
                """);
        // 3. users (user_id=7, 8 used in tests; belong to project 100)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO users (user_id, project_id, external_id)
                VALUES (7, 100, 'ext-7')
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO users (user_id, project_id, external_id)
                VALUES (8, 100, 'ext-8')
                """);
        // 4. event_categories (category_id=1, 2 used in tests)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO event_categories (category_id, project_id, name, type)
                VALUES (1, 100, 'cat-1', 'CUSTOM')
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO event_categories (category_id, project_id, name, type)
                VALUES (2, 100, 'cat-2', 'CUSTOM')
                """);
        // 5. devices (device_id=42 used in tests; belongs to user 7, project 100)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO devices (device_id, project_id, user_id, platform, push_token,
                    push_token_updated_at, token_valid, app_version, os_version)
                VALUES (42, 100, 7, 'ANDROID', 'token-42', NOW(), true, '1.0', '12.0')
                """);
    }

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 4, 30, 12, 0);

    private int insert(String uuid) {
        return repository.insertIfNotExists(
                uuid, 100L, 7L, 1L, 42L,
                OCCURRED_AT, 1, "{\"k\":\"v\"}"
        );
    }

    @Test
    @DisplayName("insertIfNotExists — 신규 event_uuid는 1을 반환하고 events 테이블에 1행 적재")
    void insertNewReturnsOne() {
        int inserted = insert("uuid-new");
        entityManager.flush();
        entityManager.clear();

        assertThat(inserted).isEqualTo(1);

        List<Event> all = repository.findAll();
        assertThat(all).hasSize(1);
        Event e = all.get(0);
        assertThat(e.getEventUuid()).isEqualTo("uuid-new");
        assertThat(e.getProjectId()).isEqualTo(100L);
        assertThat(e.getUserId()).isEqualTo(7L);
        assertThat(e.getCategoryId()).isEqualTo(1L);
        assertThat(e.getDeviceId()).isEqualTo(42L);
        assertThat(e.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(e.getSchemaVersion()).isEqualTo(1);
        assertThat(e.getEventData()).contains("\"k\"").contains("\"v\"");
    }

    @Test
    @DisplayName("insertIfNotExists — 같은 event_uuid 재insert 시 0 반환, 행 수 그대로 (멱등 보장)")
    void insertDuplicateReturnsZero() {
        int first = insert("uuid-dup");
        int second = insert("uuid-dup");
        entityManager.flush();
        entityManager.clear();

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("insertIfNotExists — eventData=null 허용 (옵셔널 페이로드)")
    void insertAllowsNullEventData() {
        int inserted = repository.insertIfNotExists(
                "uuid-null-data", 100L, 7L, 1L, 42L,
                OCCURRED_AT, 1, null
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(inserted).isEqualTo(1);
        Event e = repository.findAll().get(0);
        assertThat(e.getEventData()).isNull();
    }

    @Test
    @DisplayName("insertIfNotExists — 다른 event_uuid는 별개 행으로 적재")
    void insertDifferentUuidsCreatesSeparateRows() {
        insert("uuid-1");
        insert("uuid-2");
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("findTop20ByProjectIdAndUserIdOrderByOccurredAtDesc — 21건 중 최신 20건을 최신순으로 반환")
    void findTop20ByProjectIdAndUserId() {
        Long projectId = 100L;
        Long userId = 7L;
        LocalDateTime base = LocalDateTime.of(2026, 5, 14, 12, 0);

        for (int i = 0; i < 21; i++) {
            repository.save(Event.builder()
                    .eventUuid("uuid-top20-" + i)
                    .projectId(projectId)
                    .userId(userId)
                    .categoryId(1L)
                    .deviceId(42L)
                    .occurredAt(base.minusSeconds(i))
                    .schemaVersion(1)
                    .eventData("{}")
                    .build());
        }
        entityManager.flush();
        entityManager.clear();

        List<Event> result =
                repository.findTop20ByProjectIdAndUserIdOrderByOccurredAtDesc(projectId, userId);

        assertThat(result).hasSize(20);
        assertThat(result.get(0).getOccurredAt()).isAfter(result.get(19).getOccurredAt());
        assertThat(result.get(0).getOccurredAt()).isEqualTo(base);
        assertThat(result.get(19).getOccurredAt()).isEqualTo(base.minusSeconds(19));
    }

    @Test
    @DisplayName("findTop20ByProjectIdAndUserIdOrderByOccurredAtDesc — 다른 user 이벤트는 제외")
    void findTop20IsolatesUser() {
        Long projectId = 100L;
        LocalDateTime base = LocalDateTime.of(2026, 5, 14, 12, 0);
        // user 7L: 3건
        for (int i = 0; i < 3; i++) {
            repository.save(Event.builder()
                    .eventUuid("uuid-iso-u7-" + i)
                    .projectId(projectId).userId(7L)
                    .categoryId(1L).deviceId(42L)
                    .occurredAt(base.minusSeconds(i))
                    .schemaVersion(1).eventData("{}").build());
        }
        // user 8L: 2건
        for (int i = 0; i < 2; i++) {
            repository.save(Event.builder()
                    .eventUuid("uuid-iso-u8-" + i)
                    .projectId(projectId).userId(8L)
                    .categoryId(1L).deviceId(42L)
                    .occurredAt(base.minusSeconds(i))
                    .schemaVersion(1).eventData("{}").build());
        }
        entityManager.flush();
        entityManager.clear();

        List<Event> result =
                repository.findTop20ByProjectIdAndUserIdOrderByOccurredAtDesc(projectId, 7L);

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(e -> e.getUserId().equals(7L));
    }

    @Test
    @DisplayName("findTop20ByProjectIdAndUserIdOrderByOccurredAtDesc — 다른 project 이벤트는 제외")
    void findTop20IsolatesProject() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 14, 12, 0);
        Long userId = 7L;
        // project 100L: 3건
        for (int i = 0; i < 3; i++) {
            repository.save(Event.builder()
                    .eventUuid("uuid-iso-p100-" + i)
                    .projectId(100L)
                    .userId(userId)
                    .categoryId(1L)
                    .deviceId(42L)
                    .occurredAt(base.minusSeconds(i))
                    .schemaVersion(1)
                    .eventData("{}")
                    .build());
        }
        // project 999L: 2건 (같은 user지만 다른 project이므로 제외되어야 함)
        for (int i = 0; i < 2; i++) {
            repository.save(Event.builder()
                    .eventUuid("uuid-iso-p999-" + i)
                    .projectId(999L)
                    .userId(userId)
                    .categoryId(1L)
                    .deviceId(42L)
                    .occurredAt(base.minusSeconds(i))
                    .schemaVersion(1)
                    .eventData("{}")
                    .build());
        }
        entityManager.flush();
        entityManager.clear();

        List<Event> result =
                repository.findTop20ByProjectIdAndUserIdOrderByOccurredAtDesc(100L, userId);

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(e -> e.getProjectId().equals(100L));
    }
}
