package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentFavorite;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SegmentFavoriteRepositoryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private SegmentFavoriteRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertParentFixtures() {
        // 1. members (segment_favorites.member_id → members, and projects.owner_id → members)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO members (member_id, project_id, email, name, phone, birth_date, role_type, member_status)
                VALUES (100, NULL, 'fav-100@test.com', '즐겨찾기100', '01012345678', '1990-01-01', 'OWNER', 'ACTIVE')
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO members (member_id, project_id, email, name, phone, birth_date, role_type, member_status)
                VALUES (200, NULL, 'fav-200@test.com', '즐겨찾기200', '01098765432', '1990-01-01', 'VIEWER', 'ACTIVE')
                """);
        // 2. project
        jdbcTemplate.execute("""
                INSERT IGNORE INTO projects (project_id, name, business_no, company_auth_no, owner_id, plan)
                VALUES (1, 'fav-project', '123-45-67890', 'COMP001', 100, 'FREE')
                """);
        // 3. segments (segment_favorites.segment_id → segments.segment_id)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO segments (segment_id, project_id, name, condition_tree, schema_version, build_status, created_by, is_deleted)
                VALUES (1, 1, 'fav-seg-1', '{"logic":"AND","conditions":[]}', 4, 'PENDING', 100, false)
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO segments (segment_id, project_id, name, condition_tree, schema_version, build_status, created_by, is_deleted)
                VALUES (2, 1, 'fav-seg-2', '{"logic":"AND","conditions":[]}', 4, 'PENDING', 100, false)
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO segments (segment_id, project_id, name, condition_tree, schema_version, build_status, created_by, is_deleted)
                VALUES (3, 1, 'fav-seg-3', '{"logic":"AND","conditions":[]}', 4, 'PENDING', 100, false)
                """);
    }

    @Test
    @DisplayName("existsBySegmentIdAndMemberId — 등록 후 true, 미등록은 false")
    void existsBySegmentIdAndMemberId() {
        repository.save(favorite(1L, 100L));

        assertThat(repository.existsBySegmentIdAndMemberId(1L, 100L)).isTrue();
        assertThat(repository.existsBySegmentIdAndMemberId(1L, 101L)).isFalse();
        assertThat(repository.existsBySegmentIdAndMemberId(2L, 100L)).isFalse();
    }

    @Test
    @DisplayName("deleteBySegmentIdAndMemberId — 삭제 후 exists가 false")
    void deleteBySegmentIdAndMemberId() {
        repository.save(favorite(1L, 100L));
        repository.flush();

        repository.deleteBySegmentIdAndMemberId(1L, 100L);
        repository.flush();

        assertThat(repository.existsBySegmentIdAndMemberId(1L, 100L)).isFalse();
    }

    @Test
    @DisplayName("findByMemberId — 멤버의 모든 즐겨찾기를 반환한다")
    void findByMemberId() {
        repository.save(favorite(1L, 100L));
        repository.save(favorite(2L, 100L));
        repository.save(favorite(3L, 200L));

        List<SegmentFavorite> result = repository.findByMemberId(100L);

        assertThat(result)
                .extracting(SegmentFavorite::getSegmentId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    private static SegmentFavorite favorite(long segmentId, long memberId) {
        return SegmentFavorite.builder().segmentId(segmentId).memberId(memberId).build();
    }
}
