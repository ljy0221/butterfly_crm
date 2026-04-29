package com.b101.butterfly.customer.entity;

import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "event_id")
  private Long id;

  @Column(name = "event_uuid", nullable = false, unique = true, length = 36)
  private String eventUuid;

  @Column(name = "project_id", nullable = false)
  private Long projectId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "category_id", nullable = false)
  private Long categoryId;

  @Column(name = "device_id", nullable = false)
  private Long deviceId;

  @Column(name = "occurred_at", nullable = false)
  private LocalDateTime occurredAt;

  @Column(name = "schema_version", nullable = false)
  private Integer schemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "event_data", columnDefinition = "json")
  private String eventData;

  @Builder
  public Event(String eventUuid, Long projectId, Long userId, Long categoryId,
      Long deviceId, LocalDateTime occurredAt, Integer schemaVersion, String eventData) {
    this.eventUuid = eventUuid;
    this.projectId = projectId;
    this.userId = userId;
    this.categoryId = categoryId;
    this.deviceId = deviceId;
    this.occurredAt = occurredAt;
    this.schemaVersion = schemaVersion != null ? schemaVersion : 1;
    this.eventData = eventData;
  }
}
