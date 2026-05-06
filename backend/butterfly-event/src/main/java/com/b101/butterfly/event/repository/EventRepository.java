package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.Event;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

  Page<Event> findByProjectIdAndUserId(Long projectId, Long userId, Pageable pageable);

  Page<Event> findByProjectId(Long projectId, Pageable pageable);

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
