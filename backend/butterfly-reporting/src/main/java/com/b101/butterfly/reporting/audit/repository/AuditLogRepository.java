package com.b101.butterfly.reporting.audit.repository;

import com.b101.butterfly.reporting.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 감사 로그 저장소 (ADR-0008 v2 / ADR-0009 reporting 소유).
 *
 * <p>append-only 정책에 따라 INSERT 와 SELECT 만 사용한다. UPDATE/DELETE 는
 * Hibernate {@code @Immutable}, DB 트리거, GRANT 권한 분리 3중으로 차단된다.
 *
 * <p>{@link JpaSpecificationExecutor}로 동적 필터 검색을 지원한다 — 실제 조건 빌드는
 * {@link AuditLogSpecifications} 참조.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {
}
