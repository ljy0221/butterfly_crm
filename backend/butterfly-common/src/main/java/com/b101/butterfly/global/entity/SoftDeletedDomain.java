package com.b101.butterfly.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 논리 삭제용 베이스 (createdAt/updatedAt + is_deleted/deletedAt).
 *
 * <p>⚠️ Hibernate {@code @SQLRestriction}은 {@link MappedSuperclass}가 아닌 구체 엔티티에만
 * 적용된다. {@code is_deleted=false} 자동 필터가 필요하면 상속받는 엔티티 클래스에
 * {@code @SQLRestriction("is_deleted = false")}를 직접 명시해야 한다.
 *
 * <p>현재 적용 사례: {@code Campaign}, {@code CampaignExecution}, {@code MessageTemplate}.
 */
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SoftDeletedDomain extends BaseDomain {

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    private LocalDateTime deletedAt;

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}