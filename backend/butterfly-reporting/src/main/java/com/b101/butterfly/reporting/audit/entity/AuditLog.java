package com.b101.butterfly.reporting.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 감사 로그 엔티티 (ADR-0008 v2 스키마 + ADR-0009 소유 모듈 이전).
 *
 * <p>모든 도메인이 발행한 {@code AuditRecordedEvent}를 reporting 모듈의
 * {@code AuditEventListener}가 구독하여 본 엔티티로 저장한다.
 *
 * <p><b>append-only</b>: Hibernate {@code @Immutable}로 UPDATE 쿼리 생성을 차단한다.
 * DB 트리거(ADR-0008 §"Append-only 3중 방어선")와 GRANT 권한 분리는 운영 DB에서
 * Flyway(ADR-0013)를 통해 적용된다.
 *
 * <p><b>BaseEntity 미상속</b>: audit_logs는 {@code created_at}만 갖고 {@code updated_at}이
 * 없으므로 (append-only 정책) BaseEntity를 상속하지 않는다.
 */
@Entity
@Immutable
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private AuditLog(
            Long projectId,
            Long memberId,
            String action,
            String targetType,
            Long targetId,
            Map<String, Object> detail,
            String ipAddress,
            String userAgent
    ) {
        this.projectId = projectId;
        this.memberId = memberId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    /**
     * 감사 이벤트 페이로드로부터 새 엔티티를 만든다. {@code created_at}은 {@link #onCreate()}에서 자동 채움.
     */
    public static AuditLog create(
            Long projectId,
            Long memberId,
            String action,
            String targetType,
            Long targetId,
            Map<String, Object> detail,
            String ipAddress,
            String userAgent
    ) {
        return new AuditLog(projectId, memberId, action, targetType, targetId, detail, ipAddress, userAgent);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
