package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.UserTag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTagRepository extends JpaRepository<UserTag, Long> {

    /**
     * 한 프로젝트의 여러 사용자의 모든 tag 를 한 번에 조회. 캠페인 fan-out 시
     * userId 별 변수 map 을 만드는 진입 쿼리.
     */
    List<UserTag> findAllByProjectIdAndUserIdIn(Long projectId, Collection<Long> userIds);

    /**
     * 프로젝트(+옵션 세그먼트) 범위에서 distinct {@code tag_key} 와 보유 사용자 수를 집계.
     * 메시지 템플릿 편집 화면의 "프로젝트별 추천 변수" 패널에 사용된다.
     *
     * <p>{@code segmentId} 가 null 이면 프로젝트 전체, 값이 있으면 활성 세그먼트 멤버로 한정 —
     * 만료된 세그먼트 멤버({@code expires_at <= NOW}) 는 제외 ({@link com.b101.butterfly.segment.repository.SegmentMemberRepository#findActiveBySegmentId}
     * 과 동일 규칙).
     *
     * <p>정렬: userCount DESC, tagKey ASC (동수일 때 안정적).
     */
    @Query(value = """
            SELECT ut.tag_key AS tagKey, COUNT(DISTINCT ut.user_id) AS userCount
              FROM user_tags ut
             WHERE ut.project_id = :projectId
               AND (:segmentId IS NULL OR EXISTS (
                   SELECT 1 FROM segment_members sm
                    WHERE sm.segment_id = :segmentId
                      AND sm.user_id = ut.user_id
                      AND (sm.expires_at IS NULL OR sm.expires_at > NOW())
               ))
             GROUP BY ut.tag_key
             ORDER BY userCount DESC, tagKey ASC
             LIMIT :limit
            """, nativeQuery = true)
    List<RecommendedTagKey> findRecommendedTagKeys(
            @Param("projectId") Long projectId,
            @Param("segmentId") Long segmentId,
            @Param("limit") int limit);

    /** 집계 row projection — tag_key + 보유 user 수. */
    interface RecommendedTagKey {
        String getTagKey();
        long getUserCount();
    }
}
