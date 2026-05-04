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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SegmentRepository}의 빌드 잡 CAS 쿼리 통합 테스트 ([-178] PR1).
 *
 * <p>{@code markBuildingIfPossible / updateHeartbeat / completeBuildIfOwner / failBuildIfOwner /
 * markStaleBuildsAsFailed} 5개 메서드의 동시성 안전성을 Testcontainers MySQL로 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SegmentBuildLockIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private SegmentRepository repository;
    @Autowired private EntityManager entityManager;

    private static final String TOKEN_A = "worker-a-uuid-aaaa";
    private static final String TOKEN_B = "worker-b-uuid-bbbb";

    @Test
    @DisplayName("markBuildingIfPossible — PENDING 상태에서 점유 성공 (return 1)")
    void markBuildingFromPending() {
        Segment saved = repository.saveAndFlush(newSegment());
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        int updated =
                repository.markBuildingIfPossible(
                        saved.getId(), TOKEN_A, now, now.minusMinutes(30));
        repository.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        Segment reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBuildStatus()).isEqualTo(BuildStatus.BUILDING);
        assertThat(reloaded.getBuildToken()).isEqualTo(TOKEN_A);
        assertThat(reloaded.getBuildStartedAt()).isEqualTo(now);
        assertThat(reloaded.getBuildHeartbeatAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("markBuildingIfPossible — BUILDING + heartbeat 신선 시 다른 worker 거절 (return 0)")
    void markBuildingRejectedWhenHeartbeatFresh() {
        Segment saved = repository.saveAndFlush(newSegment());
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
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
        LocalDateTime longAgo = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS).minusHours(2);
        repository.markBuildingIfPossible(
                saved.getId(), TOKEN_A, longAgo, longAgo.minusMinutes(30));
        repository.flush();
        entityManager.clear();

        // worker-b가 지금 점유 시도, stale threshold = 10분 전 → TOKEN_A의 heartbeat가 stale
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
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
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
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
        assertThat(reloaded.getBuildHeartbeatAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("completeBuildIfOwner — 자기 token이면 READY + estimated 갱신 + token/heartbeat 정리")
    void completeBuildOwnerSucceeds() {
        Segment saved = repository.saveAndFlush(newSegment());
        LocalDateTime startedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
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
        assertThat(reloaded.getEstimatedAt()).isEqualTo(builtAt);
        assertThat(reloaded.getLastBuiltAt()).isEqualTo(builtAt);
        assertThat(reloaded.getBuildToken()).isNull();
        assertThat(reloaded.getBuildHeartbeatAt()).isNull();
    }

    @Test
    @DisplayName("completeBuildIfOwner — 다른 token이면 거절 (return 0, 상태 보존)")
    void completeBuildRejectsStaleOwner() {
        Segment saved = repository.saveAndFlush(newSegment());
        LocalDateTime startedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
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
        LocalDateTime startedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
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
        LocalDateTime longAgo = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS).minusHours(1);
        repository.markBuildingIfPossible(
                stale.getId(), TOKEN_A, longAgo, longAgo.minusMinutes(30));

        // 신선 BUILDING (heartbeat 방금)
        Segment fresh = repository.saveAndFlush(newSegment("fresh"));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        repository.markBuildingIfPossible(fresh.getId(), TOKEN_B, now, now.minusMinutes(30));

        // PENDING (변경 없음)
        Segment pending = repository.saveAndFlush(newSegment("pending"));

        repository.flush();
        entityManager.clear();

        int converted = repository.markStaleBuildsAsFailed(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS).minusMinutes(30));
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
