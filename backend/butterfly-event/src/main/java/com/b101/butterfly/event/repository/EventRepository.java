package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.Event;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

  Page<Event> findByProjectIdAndUserId(Long projectId, Long userId, Pageable pageable);

  Page<Event> findByProjectId(Long projectId, Pageable pageable);

  /** 특정 (project, category) 의 가장 최근 발생 이벤트 1건 — 카테고리 샘플 화면용. */
  Optional<Event> findTopByProjectIdAndCategoryIdOrderByOccurredAtDesc(
      Long projectId, Long categoryId);

  /** 시연용 — 특정 (project, user) 의 최근 이벤트 최대 20건, 최신순. */
  List<Event> findTop20ByProjectIdAndUserIdOrderByOccurredAtDesc(Long projectId, Long userId);

  /** 시연용 — 특정 (project, user) 의 전체 이벤트 수. */
  long countByProjectIdAndUserId(Long projectId, Long userId);

  /** 시연용 — 특정 (project, user) 의 카테고리명 기준 이벤트 수. */
  @Query("""
      SELECT COUNT(e) FROM Event e
      WHERE e.projectId = :projectId
        AND e.userId = :userId
        AND e.categoryId IN (
            SELECT c.id FROM EventCategory c
            WHERE c.projectId = :projectId AND c.name = :categoryName
        )
      """)
  long countByProjectIdAndUserIdAndCategoryName(
      @Param("projectId") Long projectId,
      @Param("userId") Long userId,
      @Param("categoryName") String categoryName
  );

  @Query("""
      SELECT e.userId AS userId,
             COUNT(e.id) AS totalEvents,
             MAX(e.occurredAt) AS lastSeenAt,
             SUM(CASE WHEN e.categoryId IN (
                 SELECT c.id FROM EventCategory c
                 WHERE c.projectId = :projectId AND c.name = :purchaseCategory
             ) THEN 1 ELSE 0 END) AS purchaseCount
      FROM Event e
      WHERE e.projectId = :projectId
        AND e.userId IN :userIds
      GROUP BY e.userId
      """)
  List<UserActivitySummaryRow> summarizeByProjectIdAndUserIds(
      @Param("projectId") Long projectId,
      @Param("userIds") List<Long> userIds,
      @Param("purchaseCategory") String purchaseCategory
  );

  /**
   * event_uuid 기준 멱등 insert. 중복이면 0, 신규면 1 반환. (events insert 결과로 event_daily_agg 증분 여부를 결정하는 용도)
   */
  @Modifying
  @Query(value = """
      INSERT IGNORE INTO events
          (event_uuid, project_id, user_id, category_id, device_id,
           occurred_at, schema_version, event_data, created_at, updated_at)
      VALUES
          (:eventUuid, :projectId, :userId, :categoryId, :deviceId,
           :occurredAt, :schemaVersion, :eventData, NOW(), NOW())
      """, nativeQuery = true)
  int insertIfNotExists(
      @Param("eventUuid") String eventUuid,
      @Param("projectId") Long projectId,
      @Param("userId") Long userId,
      @Param("categoryId") Long categoryId,
      @Param("deviceId") Long deviceId,
      @Param("occurredAt") LocalDateTime occurredAt,
      @Param("schemaVersion") Integer schemaVersion,
      @Param("eventData") String eventData
  );
}
