package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.segment.entity.SegmentMemberId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface SegmentMemberRepository extends JpaRepository<SegmentMember, SegmentMemberId> {

    long countBySegmentId(Long segmentId);

    Page<SegmentMember> findBySegmentId(Long segmentId, Pageable pageable);

    /**
     * 활성(미만료) 멤버만 페이지 조회 — Phase 8-1 대상 유저 목록 API용.
     *
     * <p>Codex 합의: 만료 배치 cron 사이 시점에 stale row가 보일 수 있으므로
     * {@code expires_at IS NULL OR expires_at > now} 필터를 query 단계에서 강제.
     * cumulative 매칭(expires_at NULL)은 자동 포함.
     */
    @Query(
            "SELECT sm FROM SegmentMember sm "
                    + "WHERE sm.segmentId = :segmentId "
                    + "AND (sm.expiresAt IS NULL OR sm.expiresAt > :now)")
    Page<SegmentMember> findActiveBySegmentId(
            @Param("segmentId") Long segmentId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    /**
     * rebuild 시점에 기존 멤버 일괄 삭제. 새 매칭으로 INSERT 전 호출.
     *
     * @return 삭제된 row 수 (estimated 기준 비교용)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM SegmentMember sm WHERE sm.segmentId = :segmentId")
    int deleteBySegmentId(@Param("segmentId") Long segmentId);

    /**
     * 실시간 매칭 UPSERT. 기존 row가 있으면 matched_at 갱신 + expires_at은 cumulative 우선
     * 정책으로 갱신 (Codex 합의 D3):
     * <ul>
     *   <li>기존 expires_at IS NULL (cumulative win) → NULL 유지</li>
     *   <li>새 expires_at IS NULL (이번 매칭이 cumulative) → NULL로 전환</li>
     *   <li>둘 다 not null → GREATEST</li>
     * </ul>
     *
     * <p>nativeQuery + ON DUPLICATE KEY UPDATE — JPA save()는 race condition에 unique 위반
     * 가능. atomic UPSERT가 필요.
     *
     * <p>{@code expiresAt} 파라미터에 null을 그대로 전달하려면 호출자가 명시 null 바인딩.
     * NamedParameterJdbcTemplate은 문제없으나, JPA Native Query의 {@code @Param}은 null OK.
     *
     * @return 영향 row 수 (1 = INSERT, 2 = UPDATE — MySQL ON DUPLICATE KEY UPDATE 의미)
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    INSERT INTO segment_members (segment_id, user_id, matched_at, expires_at)
                    VALUES (:segmentId, :userId, :matchedAt, :expiresAt)
                    ON DUPLICATE KEY UPDATE
                      matched_at = VALUES(matched_at),
                      expires_at = CASE
                        WHEN expires_at IS NULL OR VALUES(expires_at) IS NULL THEN NULL
                        ELSE GREATEST(expires_at, VALUES(expires_at))
                      END
                    """,
            nativeQuery = true)
    int upsertMember(
            @Param("segmentId") Long segmentId,
            @Param("userId") Long userId,
            @Param("matchedAt") LocalDateTime matchedAt,
            @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 만료된 멤버 일괄 삭제. KST 03:00 cron이 호출.
     *
     * <p>{@code expires_at IS NULL}(Cumulative semantic)은 자동 제외. {@code <= now}로 경계
     * 시점 포함 (Codex 합의 D4).
     *
     * @return 삭제된 row 수
     */
    @Modifying
    @Transactional
    @Query(
            "DELETE FROM SegmentMember sm WHERE sm.expiresAt IS NOT NULL AND sm.expiresAt <= :now")
    int deleteExpiredMembers(@Param("now") LocalDateTime now);
}
