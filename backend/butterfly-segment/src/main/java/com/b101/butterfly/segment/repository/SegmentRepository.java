package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.entity.Segment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Segment Repository. {@link Segment}는 {@code @SQLRestriction("is_deleted = false")}가 걸려 있어
 * 표준 메서드(findById, findAll 등)도 자동으로 soft-delete된 행을 제외한다.
 *
 * <p>빌드 잡 동시성 제어용 CAS 쿼리는 [-178] Phase 6-2에서 추가 — Codex 합의 안전장치
 * (build_token + build_heartbeat_at + stale 감지).
 */
public interface SegmentRepository extends JpaRepository<Segment, Long> {

    Page<Segment> findByProjectId(Long projectId, Pageable pageable);

    boolean existsByProjectIdAndName(Long projectId, String name);

    /**
     * 활성 segment 캐시 적재용 — 프로젝트의 READY 상태 segment 모두 로드.
     * EVENT-only 트리 + categoryId 추출은 호출자가 conditionTree 검사로 분류.
     */
    List<Segment> findByProjectIdAndBuildStatus(Long projectId, BuildStatus buildStatus);

    /**
     * 빌드 시작 CAS — {@code BUILDING}이 아닌 row, 또는 {@code BUILDING}이지만 heartbeat가
     * stale인 row만 새 worker가 점유한다. 정상 동작 중인 worker(heartbeat 신선)는 보호.
     *
     * <p>{@code WHERE} 조건:
     * <ul>
     *   <li>{@code build_status <> 'BUILDING'} — PENDING/READY/FAILED는 자유 진입
     *   <li>{@code OR build_heartbeat_at IS NULL} — 안전망(이전 빌드 잔재)
     *   <li>{@code OR build_heartbeat_at < :staleThreshold} — stale 빌드 강제 인수
     * </ul>
     *
     * @return 1 = 점유 성공, 0 = 다른 worker가 점유 중
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    UPDATE Segment s
                       SET s.buildStatus = com.b101.butterfly.segment.entity.BuildStatus.BUILDING,
                           s.buildToken = :token,
                           s.buildStartedAt = :now,
                           s.buildHeartbeatAt = :now
                     WHERE s.id = :segmentId
                       AND (s.buildStatus <> com.b101.butterfly.segment.entity.BuildStatus.BUILDING
                            OR s.buildHeartbeatAt IS NULL
                            OR s.buildHeartbeatAt < :staleThreshold)
                    """)
    int markBuildingIfPossible(
            @Param("segmentId") Long segmentId,
            @Param("token") String token,
            @Param("now") LocalDateTime now,
            @Param("staleThreshold") LocalDateTime staleThreshold);

    /**
     * chunk 처리 중 heartbeat 갱신. 자기 token이 들고 있는 row만 갱신 — stale worker 보호.
     *
     * @return 1 = 갱신, 0 = 이미 다른 worker로 인수됐거나 종료됨
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    UPDATE Segment s
                       SET s.buildHeartbeatAt = :now
                     WHERE s.id = :segmentId
                       AND s.buildToken = :token
                    """)
    int updateHeartbeat(
            @Param("segmentId") Long segmentId,
            @Param("token") String token,
            @Param("now") LocalDateTime now);

    /**
     * 빌드 성공 마무리 — 자기 token이 들고 있는 row만 {@code READY}로 전환하고 estimated 갱신,
     * token/heartbeat 정리. stale worker가 늦게 도착해 결과를 덮어쓰는 사고 방지.
     *
     * @return 1 = 성공, 0 = 다른 worker로 인수됐거나 종료됨
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    UPDATE Segment s
                       SET s.buildStatus = com.b101.butterfly.segment.entity.BuildStatus.READY,
                           s.estimatedCount = :estimatedCount,
                           s.estimatedAt = :builtAt,
                           s.lastBuiltAt = :builtAt,
                           s.buildToken = NULL,
                           s.buildHeartbeatAt = NULL
                     WHERE s.id = :segmentId
                       AND s.buildToken = :token
                    """)
    int completeBuildIfOwner(
            @Param("segmentId") Long segmentId,
            @Param("token") String token,
            @Param("estimatedCount") Integer estimatedCount,
            @Param("builtAt") LocalDateTime builtAt);

    /**
     * 빌드 실패 마무리 — 자기 token이 들고 있는 row만 {@code FAILED}로 전환하고 token/heartbeat 정리.
     *
     * @return 1 = 성공, 0 = 다른 worker로 인수됐거나 종료됨
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    UPDATE Segment s
                       SET s.buildStatus = com.b101.butterfly.segment.entity.BuildStatus.FAILED,
                           s.buildToken = NULL,
                           s.buildHeartbeatAt = NULL
                     WHERE s.id = :segmentId
                       AND s.buildToken = :token
                    """)
    int failBuildIfOwner(@Param("segmentId") Long segmentId, @Param("token") String token);

    /**
     * stale 복구 — 모든 stale {@code BUILDING} row를 {@code FAILED}로 일괄 전환. PR1 단계에서는
     * 단순 FAILED 전환만, 별도 cron은 PR3에서 추가.
     *
     * @return 전환된 row 수
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    UPDATE Segment s
                       SET s.buildStatus = com.b101.butterfly.segment.entity.BuildStatus.FAILED,
                           s.buildToken = NULL,
                           s.buildHeartbeatAt = NULL
                     WHERE s.buildStatus = com.b101.butterfly.segment.entity.BuildStatus.BUILDING
                       AND s.buildHeartbeatAt IS NOT NULL
                       AND s.buildHeartbeatAt < :staleThreshold
                    """)
    int markStaleBuildsAsFailed(@Param("staleThreshold") LocalDateTime staleThreshold);
}
