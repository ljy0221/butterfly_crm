package com.b101.butterfly.segment.entity;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Segment} 도메인 메서드 단위 테스트 ([-178] PR1).
 *
 * <p>빌드 라이프사이클(startBuild → heartbeat → markReady/markFailed) 상태 전이 검증.
 * 동시성 안전한 CAS 동작은 {@code SegmentRepository}의 통합 테스트가 검증한다.
 */
class SegmentTest {

    @Test
    @DisplayName("startBuild — BUILDING 전환 + token/startedAt/firstHeartbeat 모두 기록")
    void startBuildSetsAllFields() {
        Segment segment = sample();
        String token = "abcd-1234";
        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 4, 10, 0);

        segment.startBuild(token, startedAt);

        assertThat(segment.getBuildStatus()).isEqualTo(BuildStatus.BUILDING);
        assertThat(segment.getBuildToken()).isEqualTo(token);
        assertThat(segment.getBuildStartedAt()).isEqualTo(startedAt);
        assertThat(segment.getBuildHeartbeatAt()).isEqualTo(startedAt);
    }

    @Test
    @DisplayName("heartbeat — buildHeartbeatAt만 갱신, 다른 빌드 필드는 보존")
    void heartbeatUpdatesOnlyHeartbeatTimestamp() {
        Segment segment = sample();
        String token = "abcd-1234";
        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 4, 10, 0);
        LocalDateTime later = startedAt.plusSeconds(30);
        segment.startBuild(token, startedAt);

        segment.heartbeat(later);

        assertThat(segment.getBuildHeartbeatAt()).isEqualTo(later);
        assertThat(segment.getBuildToken()).isEqualTo(token);
        assertThat(segment.getBuildStartedAt()).isEqualTo(startedAt);
        assertThat(segment.getBuildStatus()).isEqualTo(BuildStatus.BUILDING);
    }

    @Test
    @DisplayName("markReady — READY 전환 + estimated 갱신 + token/heartbeat 정리, startedAt 보존")
    void markReadyClearsLockFieldsButKeepsStartedAt() {
        Segment segment = sample();
        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 4, 10, 0);
        LocalDateTime builtAt = startedAt.plusMinutes(5);
        segment.startBuild("abcd", startedAt);

        segment.markReady(15000, builtAt);

        assertThat(segment.getBuildStatus()).isEqualTo(BuildStatus.READY);
        assertThat(segment.getEstimatedCount()).isEqualTo(15000);
        assertThat(segment.getEstimatedAt()).isEqualTo(builtAt);
        assertThat(segment.getLastBuiltAt()).isEqualTo(builtAt);
        // 락 필드는 정리
        assertThat(segment.getBuildToken()).isNull();
        assertThat(segment.getBuildHeartbeatAt()).isNull();
        // startedAt은 마지막 시작 시각으로 보존
        assertThat(segment.getBuildStartedAt()).isEqualTo(startedAt);
    }

    @Test
    @DisplayName("markFailed — FAILED 전환 + token/heartbeat 정리, startedAt 보존")
    void markFailedClearsLockFields() {
        Segment segment = sample();
        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 4, 10, 0);
        segment.startBuild("abcd", startedAt);

        segment.markFailed();

        assertThat(segment.getBuildStatus()).isEqualTo(BuildStatus.FAILED);
        assertThat(segment.getBuildToken()).isNull();
        assertThat(segment.getBuildHeartbeatAt()).isNull();
        assertThat(segment.getBuildStartedAt()).isEqualTo(startedAt);
    }

    @Test
    @DisplayName("초기 상태 — buildToken/heartbeat/startedAt 모두 null, status PENDING")
    void freshSegmentHasNoBuildFields() {
        Segment segment = sample();

        assertThat(segment.getBuildStatus()).isEqualTo(BuildStatus.PENDING);
        assertThat(segment.getBuildToken()).isNull();
        assertThat(segment.getBuildHeartbeatAt()).isNull();
        assertThat(segment.getBuildStartedAt()).isNull();
    }

    @Test
    @DisplayName("replaceConditionTree — buildStatus PENDING 리셋 (estimatedCount/At도 null)")
    void replaceConditionTreeResetsBuildState() {
        Segment segment = sample();
        segment.markReady(100, LocalDateTime.now());

        GroupNode newTree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new TagNode(
                                        "region",
                                        Operator.EQ,
                                        JsonNodeFactory.instance.textNode("부산"))));
        segment.replaceConditionTree(newTree);

        assertThat(segment.getBuildStatus()).isEqualTo(BuildStatus.PENDING);
        assertThat(segment.getEstimatedCount()).isNull();
        assertThat(segment.getEstimatedAt()).isNull();
    }

    private static Segment sample() {
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
                .name("VIP")
                .description("desc")
                .conditionTree(tree)
                .createdBy(17L)
                .build();
    }
}
