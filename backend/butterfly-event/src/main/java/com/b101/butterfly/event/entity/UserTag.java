package com.b101.butterfly.event.entity;

import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자별 key-value attribute. 캠페인 발송 시 개인화 변수({@code {{var}}}) 치환의 데이터 source.
 *
 * <p>(projectId, userId, tagKey) UNIQUE — 한 사용자의 한 tag_key 는 단 하나의 값.
 * SDK 또는 운영 API 가 사용자별 속성을 적재한다.
 */
@Entity
@Table(
        name = "user_tags",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_tags_project_user_key",
                columnNames = {"project_id", "user_id", "tag_key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_tag_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tag_key", nullable = false, length = 100)
    private String tagKey;

    @Column(name = "tag_value", nullable = false, length = 500)
    private String tagValue;

    @Builder
    public UserTag(Long projectId, Long userId, String tagKey, String tagValue) {
        this.projectId = projectId;
        this.userId = userId;
        this.tagKey = tagKey;
        this.tagValue = tagValue;
    }
}
