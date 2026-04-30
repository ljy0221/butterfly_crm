package com.b101.butterfly.event.entity;

import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategoryType type;

    @Column(length = 500)
    private String description;

    @Builder
    public EventCategory(Long projectId, String name, EventCategoryType type, String description) {
        this.projectId = projectId;
        this.name = name;
        this.type = type != null ? type : EventCategoryType.CUSTOM;
        this.description = description;
    }

    public void updateDescription(String description) {
        this.description = description;
    }
}
