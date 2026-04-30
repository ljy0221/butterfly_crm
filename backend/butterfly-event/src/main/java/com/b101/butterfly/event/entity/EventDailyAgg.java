package com.b101.butterfly.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_daily_agg")
@IdClass(EventDailyAggId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventDailyAgg {

    @Id
    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Id
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Id
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_count", nullable = false)
    private Integer eventCount;

    @Column(name = "first_at", nullable = false)
    private LocalDateTime firstAt;

    @Column(name = "last_at", nullable = false)
    private LocalDateTime lastAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
