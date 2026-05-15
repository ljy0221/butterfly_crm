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
     * <p>추가 차단 (S14P31B101-269): {@code build_token} 이 {@code 'cancel-'} 로 시작하면 점유 불가.
     * 운영 중 빌드 취소가 필요할 때 cancel marker 를 token 에 박아두면 진행 중 KILL 직후의 retry
     * 가 다시 점유하는 race 를 차단한다. 새 rebuild 요청 ({@link Segment#markPending()}) 이 들어오면
     * marker 가 자동으로 해제 (token=NULL) 되어 정상 흐름 복귀.
     *
     * @return 1 = 점유 성공, 0 = 다른 worker가 점유 중 또는 cancel marker 차단
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
                       AND (s.buildToken IS NULL OR s.buildToken NOT LIKE 'cancel-%')
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
     * 빌드 취소 (S14P31B101-269). 단일 UPDATE 로 status=FAILED 강등 + cancel marker token 마킹 +
     * heartbeat 갱신 (marker 가 stale 판정 안 되도록).
     *
     * <p>BUILDING/PENDING 상태에서만 의미가 있으므로 그 외 상태는 0 반환 (멱등 처리). marker 가 token 에
     * 박혀 있는 동안 worker 의 {@link #markBuildingIfPossible} 는 점유 불가 — 진행 중 INSERT-SELECT 가
     * 끝나고 retry 또는 새 메시지가 도착해도 빌드 시작이 차단된다. UX 측에는 즉시 FAILED 로 노출되므로
     * stale UI 문제 회피.
     *
     * <p>본 메서드는 worker 의 다음 step 과 새 메시지 처리만 차단. 진행 중인 INSERT-SELECT 자체를
     * 끊으려면 DBA 권한으로 직접 KILL 이 필요 (별개 흐름).
     *
     * @return 1 = 취소 성공, 0 = 취소 대상 없음 (이미 READY/FAILED 또는 segment 없음)
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    UPDATE Segment s
                       SET s.buildStatus = com.b101.butterfly.segment.entity.BuildStatus.FAILED,
                           s.buildToken = :cancelMarker,
                           s.buildHeartbeatAt = :now
                     WHERE s.id = :segmentId
                       AND s.buildStatus IN (com.b101.butterfly.segment.entity.BuildStatus.BUILDING,
                                              com.b101.butterfly.segment.entity.BuildStatus.PENDING)
                    """)
    int cancelBuildIfPossible(
            @Param("segmentId") Long segmentId,
            @Param("cancelMarker") String cancelMarker,
            @Param("now") LocalDateTime now);

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

    /**
     * 강제 FAILED 강등 — token 검사 없이 segments status 를 FAILED 로 전환하고 token/heartbeat 정리.
     *
     * <p><b>recovery 전용</b>: Kafka {@code DefaultErrorHandler} 의 최종 실패 콜백
     * ({@code ConsumerRecordRecoverer}) 에서만 호출. retry 모두 소진된 메시지는 worker 가
     * token 을 잡지 못했을 가능성이 높아 {@link #failBuildIfOwner} 의 token 매칭으로는 회수 불가 →
     * 강제 강등이 필요. 정상 흐름의 실패는 {@link #failBuildIfOwner} 사용.
     *
     * @return 1 = 강등 성공, 0 = segment 가 이미 삭제됐거나 존재하지 않음
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
                    """)
    int forceFailById(@Param("segmentId") Long segmentId);
}
