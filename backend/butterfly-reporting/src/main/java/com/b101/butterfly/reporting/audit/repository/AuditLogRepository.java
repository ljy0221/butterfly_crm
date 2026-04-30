package com.b101.butterfly.reporting.audit.repository;

import com.b101.butterfly.reporting.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 감사 로그 저장소 (ADR-0008 v2 / ADR-0009 reporting 소유).
 *
 * <p>append-only 정책에 따라 INSERT 와 SELECT 만 사용한다. UPDATE/DELETE 는
 * Hibernate {@code @Immutable}, DB 트리거, GRANT 권한 분리 3중으로 차단된다.
 *
 * <p>조회 API(필터·페이징·CSV)는 별도 PR에서 추가한다 (감사 로그 조회 화면).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
