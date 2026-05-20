package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SegmentMemberRepositoryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private SegmentMemberRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertParentFixtures() {
        // 1. member (owner of projects)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO members (member_id, project_id, email, name, phone, birth_date, role_type, member_status)
                VALUES (1, NULL, 'seg-member-test@test.com', '세그멤버', '01012345678', '1990-01-01', 'OWNER', 'ACTIVE')
                """);
        // 2. project
        jdbcTemplate.execute("""
                INSERT IGNORE INTO projects (project_id, name, business_no, company_auth_no, owner_id, plan)
                VALUES (1, 'seg-member-project', '123-45-67890', 'COMP001', 1, 'FREE')
                """);
        // 3. segments (segment_members.segment_id → segments.segment_id)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO segments (segment_id, project_id, name, condition_tree, schema_version, build_status, created_by, is_deleted)
                VALUES (1, 1, 'seg-1', '{"logic":"AND","conditions":[]}', 4, 'PENDING', 1, false)
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO segments (segment_id, project_id, name, condition_tree, schema_version, build_status, created_by, is_deleted)
                VALUES (2, 1, 'seg-2', '{"logic":"AND","conditions":[]}', 4, 'PENDING', 1, false)
                """);
        // 4. users (segment_members.user_id → users.user_id)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO users (user_id, project_id, external_id)
                VALUES (100, 1, 'ext-100')
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO users (user_id, project_id, external_id)
                VALUES (101, 1, 'ext-101')
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO users (user_id, project_id, external_id)
                VALUES (102, 1, 'ext-102')
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO users (user_id, project_id, external_id)
                VALUES (200, 1, 'ext-200')
                """);
    }

    @Test
    @DisplayName("countBySegmentId는 같은 세그먼트의 멤버 수를 반환한다")
    void countBySegmentId() {
        repository.save(member(1L, 100L));
        repository.save(member(1L, 101L));
        repository.save(member(2L, 100L));

        assertThat(repository.countBySegmentId(1L)).isEqualTo(2);
        assertThat(repository.countBySegmentId(2L)).isEqualTo(1);
        assertThat(repository.countBySegmentId(99L)).isZero();
    }

    @Test
    @DisplayName("findBySegmentId는 같은 세그먼트의 멤버를 페이지로 반환한다")
    void findBySegmentIdScopesBySegment() {
        repository.save(member(1L, 100L));
        repository.save(member(1L, 101L));
        repository.save(member(2L, 200L));

        Page<SegmentMember> page = repository.findBySegmentId(1L, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(SegmentMember::getUserId)
                .containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    @DisplayName("expiresAt이 nullable로 저장된다 (cumulative period 시나리오)")
    void expiresAtAllowsNull() {
        SegmentMember saved =
                repository.save(SegmentMember.builder().segmentId(1L).userId(100L).build());

        assertThat(saved.getExpiresAt()).isNull();
        assertThat(saved.getMatchedAt()).isNotNull();
    }

    @Test
    @DisplayName("variables snapshot은 Map으로 저장하고 다시 조회할 수 있다")
    void variablesSnapshotRoundTrip() {
        LocalDateTime capturedAt = LocalDateTime.of(2026, 5, 18, 10, 0);
        SegmentMember saved =
                repository.save(
                        SegmentMember.builder()
                                .segmentId(1L)
                                .userId(100L)
                                .variables(Map.of("name", "Alice", "grade", "VIP"))
                                .variablesCapturedAt(capturedAt)
                                .build());

        assertThat(saved.getVariables())
                .containsEntry("name", "Alice")
                .containsEntry("grade", "VIP");
        assertThat(saved.getVariablesCapturedAt()).isEqualTo(capturedAt);
    }

    @Test
    @DisplayName("findActiveBySegmentId — expires_at IS NULL 또는 > now 만 반환 ([-119] Phase 8-1)")
    void findActiveBySegmentIdFiltersExpired() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 4, 12, 0);

        // cumulative (expires_at NULL) — 포함
        repository.save(SegmentMember.builder().segmentId(1L).userId(100L).build());
        // 미만료 (now 이후) — 포함
        repository.save(
                SegmentMember.builder()
                        .segmentId(1L)
                        .userId(101L)
                        .expiresAt(now.plusDays(7))
                        .build());
        // 만료 (now 이전) — 제외
        repository.save(
                SegmentMember.builder()
                        .segmentId(1L)
                        .userId(102L)
                        .expiresAt(now.minusDays(1))
                        .build());
        // 다른 segment — 제외
        repository.save(SegmentMember.builder().segmentId(2L).userId(200L).build());

        Page<SegmentMember> page =
                repository.findActiveBySegmentId(1L, now, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(SegmentMember::getUserId)
                .containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    @DisplayName("findActiveBySegmentIdAfterUserId — user_id cursor로 활성 멤버와 변수 snapshot을 조회한다")
    void findActiveBySegmentIdAfterUserIdUsesCursor() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 4, 12, 0);
        repository.save(
                SegmentMember.builder()
                        .segmentId(1L)
                        .userId(100L)
                        .variables(Map.of("name", "Alice"))
                        .build());
        repository.save(
                SegmentMember.builder()
                        .segmentId(1L)
                        .userId(101L)
                        .variables(Map.of("name", "Bob"))
                        .build());
        repository.save(
                SegmentMember.builder()
                        .segmentId(1L)
                        .userId(102L)
                        .expiresAt(now.minusDays(1))
                        .variables(Map.of("name", "Expired"))
                        .build());

        Page<SegmentMember> page =
                repository.findActiveBySegmentIdAfterUserId(1L, 100L, now, PageRequest.ofSize(10));

        assertThat(page.getContent()).extracting(SegmentMember::getUserId)
                .containsExactly(101L);
        assertThat(page.getContent().get(0).getVariables()).containsEntry("name", "Bob");
    }

    private static SegmentMember member(long segmentId, long userId) {
        return SegmentMember.builder()
                .segmentId(segmentId)
                .userId(userId)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }
}
