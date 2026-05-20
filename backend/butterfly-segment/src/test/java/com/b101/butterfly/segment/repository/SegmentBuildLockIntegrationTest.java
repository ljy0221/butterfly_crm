package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link SegmentRepository}의 빌드 잡 CAS 쿼리 통합 테스트 ([-178] PR1).
 *
 * <p>{@code markBuildingIfPossible / updateHeartbeat / completeBuildIfOwner / failBuildIfOwner /
 * markStaleBuildsAsFailed} 5개 메서드의 동시성 안전성을 Testcontainers MySQL로 검증.
 *
 * <p>시간 비교는 {@code isCloseTo within 1 SECOND} — Java LocalDateTime 나노초 정밀도와
 * MySQL DATETIME 정밀도 사이 환경 의존성(timezone/MySQL driver/JVM 정밀도)을 회피.
 * CAS 동작/token/status가 핵심이고 정밀 시간은 부수적 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SegmentBuildLockIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private SegmentRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertParentFixtures() {
        // members must exist before projects (projects.owner_id → members)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO members (member_id, project_id, email, name, phone, birth_date, role_type, member_status)
                VALUES (17, NULL, 'lock-test@test.com', '락테스트', '01012345678', '1990-01-01', 'OWNER', 'ACTIVE')
                """);
        // projects.owner_id = 17
        jdbcTemplate.execute("""
                INSERT IGNORE INTO projects (project_id, name, business_no, company_auth_no, owner_id, plan)
                VALUES (1, 'lock-project', '123-45-67890', 'COMP001', 17, 'FREE')
                """);
    }

    private static final String TOKEN_A = "worker-a-uuid-aaaa";
    private static final String TOKEN_B = "worker-b-uuid-bbbb";

    @Test
    @DisplayName("markBuildingIfPossible — PENDING 상태에서 점유 성공 (return 1)")
    void markBuildingFromPending() {
        Segment saved = repository.saveAndFlush(newSegment());
        LocalDateTime now = LocalDateTime.now();

        int updated =
                repository.markBuildingIfPossible(
                        saved.getId(), TOKEN_A, now, now.minusMinutes(30));
        repository.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        Segment reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBuildStatus()).isEqualTo(BuildStatus.BUILDING);
        assertThat(reloaded.getBuildToken()).isEqualTo(TOKEN_A);
        assertThat(reloaded.getBuildStartedAt()).isCloseTo(now, within(1, ChronoUnit.SECONDS));
        assertThat(reloaded.getBuildHeartbeatAt()).isCloseTo(now, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("markBuildingIfPossible — BUILDING + heartbeat 신선 시 다른 worker 거절 (return 0)")
    void markBuildingRejectedWhenHeartbeatFresh() {
        Segment saved = repository.saveAndFlush(newSegment());
        LocalDateTime now = LocalDateTime.now();
        repository.markBuildingIfPossible(saved.getId(), TOKEN_A, now, now.minusMinutes(30));
        repository.flush();
        entityManager.clear();

        // worker-b가 30초 후 점유 시도, stale threshold = 10분 전
        LocalDateTime later = now.plusSeconds(30);
        int updated =
                repository.markBuildingIfPossible(
                        saved.getId(), TOKEN_B, later, later.minusMinutes(10));
        repository.flush();
        entityManager.clear();

        assertThat(updated).isZero();
        Segment reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBuildToken()).isEqualTo(TOKEN_A); // 원래 worker 유지
    }

    @Test
    @DisplayName("markBuildingIfPossible — BUILDING + heartbeat stale 시 새 worker 인수 (return 1)")
    void markBuildingTakesOverStale() {
        Segment saved = repository.saveAndFlush(newSegment());
        LocalDateTime longAgo = LocalDateTime.now().minusHours(2);
        repository.markBuildingIfPossible(
                saved.getId(), TOKEN_A, longAgo, longAgo.minusMinutes(30));
        repository.flush();
        entityManager.clear();

        // worker-b가 지금 점유 시도, stale threshold = 10분 전 → TOKEN_A의 heartbeat가 stale
        LocalDateTime now = LocalDateTime.now();
        int updated =
                repository.markBuildingIfPossible(
                        saved.getId(), TOKEN_B, now, now.minusMinutes(10));
        repository.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        Segment reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBuildToken()).isEqualTo(TOKEN_B); // 새 worker가 인수
    }

    @Test
    @DisplayName("updateHeartbeat — 자기 token이면 갱신 (return 1), 다른 token이면 거절 (return 0)")
    void updateHeartbeatRespectsTokenOwnership() {
        Segment saved = repository.saveAndFlush(newSegment());
        LocalDateTime now = LocalDateTime.now();
        repository.markBuildingIfPossible(saved.getId(), TOKEN_A, now, now.minusMinutes(30));
        repository.flush();

        LocalDateTime later = now.plusSeconds(30);
        int updatedA = repository.updateHeartbeat(saved.getId(), TOKEN_A, later);
        int updatedB = repository.updateHeartbeat(saved.getId(), TOKEN_B, later);
        repository.flush();
        entityManager.clear();

        assertThat(updatedA).isEqualTo(1);
        assertThat(updatedB).isZero();
        Segment reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBuildHeartbeatAt()).isCloseTo(later, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("completeBuildIfOwner — 자기 token이면 READY + estimated 갱신 + token/heartbeat 정리")
    void completeBuildOwnerSucceeds() {
        Segment saved = repository.saveAndFlush(newSegment());
        LocalDateTime startedAt = LocalDateTime.now();
        repository.markBuildingIfPossible(
                saved.getId(), TOKEN_A, startedAt, startedAt.minusMinutes(30));
        repository.flush();

        LocalDateTime builtAt = startedAt.plusMinutes(2);
        int updated = repository.completeBuildIfOwner(saved.getId(), TOKEN_A, 12345, builtAt);
        repository.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        Segment reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBuildStatus()).isEqualTo(BuildStatus.READY);
        assertThat(reloaded.getEstimatedCount()).isEqualTo(12345);
        assertThat(reloaded.getEstimatedAt()).isCloseTo(builtAt, within(1, ChronoUnit.SECONDS));
        assertThat(reloaded.getLastBuiltAt()).isCloseTo(builtAt, within(1, ChronoUnit.SECONDS));
        assertThat(reloaded.getBuildToken()).isNull();
        assertThat(reloaded.getBuildHeartbeatAt()).isNull();
    }

    @Test
    @DisplayName("completeBuildIfOwner — 다른 token이면 거절 (return 0, 상태 보존)")
    void completeBuildRejectsStaleOwner() {
        Segment saved = repository.saveAndFlush(newSegment());
        LocalDateTime startedAt = LocalDateTime.now();
        repository.markBuildingIfPossible(
                saved.getId(), TOKEN_A, startedAt, startedAt.minusMinutes(30));
        repository.flush();

        int updated =
                repository.completeBuildIfOwner(
                        saved.getId(), TOKEN_B, 99999, startedAt.plusMinutes(2));
        repository.flush();
        entityManager.clear();

        assertThat(updated).isZero();
        Segment reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBuildStatus()).isEqualTo(BuildStatus.BUILDING);
        assertThat(reloaded.getBuildToken()).isEqualTo(TOKEN_A);
    }

    @Test
    @DisplayName("failBuildIfOwner — 자기 token이면 FAILED 전환, 다른 token이면 거절")
    void failBuildRespectsOwnership() {
        Segment saved = repository.saveAndFlush(newSegment());
        LocalDateTime startedAt = LocalDateTime.now();
        repository.markBuildingIfPossible(
                saved.getId(), TOKEN_A, startedAt, startedAt.minusMinutes(30));
        repository.flush();

        int rejectedB = repository.failBuildIfOwner(saved.getId(), TOKEN_B);
        int updatedA = repository.failBuildIfOwner(saved.getId(), TOKEN_A);
        repository.flush();
        entityManager.clear();

        assertThat(rejectedB).isZero();
        assertThat(updatedA).isEqualTo(1);
        Segment reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBuildStatus()).isEqualTo(BuildStatus.FAILED);
        assertThat(reloaded.getBuildToken()).isNull();
        assertThat(reloaded.getBuildHeartbeatAt()).isNull();
    }

    @Test
    @DisplayName("markStaleBuildsAsFailed — stale BUILDING만 FAILED (신선/PENDING 보존)")
    void staleBuildsConvertedToFailed() {
        // stale BUILDING (heartbeat 1시간 전)
        Segment stale = repository.saveAndFlush(newSegment());
        LocalDateTime longAgo = LocalDateTime.now().minusHours(1);
        repository.markBuildingIfPossible(
                stale.getId(), TOKEN_A, longAgo, longAgo.minusMinutes(30));

        // 신선 BUILDING (heartbeat 방금)
        Segment fresh = repository.saveAndFlush(newSegment("fresh"));
        LocalDateTime now = LocalDateTime.now();
        repository.markBuildingIfPossible(fresh.getId(), TOKEN_B, now, now.minusMinutes(30));

        // PENDING (변경 없음)
        Segment pending = repository.saveAndFlush(newSegment("pending"));

        repository.flush();
        entityManager.clear();

        int converted = repository.markStaleBuildsAsFailed(LocalDateTime.now().minusMinutes(30));
        repository.flush();
        entityManager.clear();

        assertThat(converted).isEqualTo(1);
        assertThat(repository.findById(stale.getId()).orElseThrow().getBuildStatus())
                .isEqualTo(BuildStatus.FAILED);
        assertThat(repository.findById(fresh.getId()).orElseThrow().getBuildStatus())
                .isEqualTo(BuildStatus.BUILDING);
        assertThat(repository.findById(pending.getId()).orElseThrow().getBuildStatus())
                .isEqualTo(BuildStatus.PENDING);
    }

    private Segment newSegment() {
        return newSegment("VIP");
    }

    private Segment newSegment(String name) {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new TagNode(
                                        "region",
                                        Operator.EQ,
                                        JsonNodeFactory.instance.textNode("서울"))));
        return Segment.builder()
                .projectId(1L)
                .name(name)
                .description("desc")
                .conditionTree(tree)
                .createdBy(17L)
                .build();
    }
}
