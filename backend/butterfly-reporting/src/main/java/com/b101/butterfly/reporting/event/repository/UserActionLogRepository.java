package com.b101.butterfly.reporting.event.repository;

import com.b101.butterfly.reporting.event.entity.UserActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 반응 로그 저장소. Append-only — INSERT/SELECT 만 사용한다 (UPDATE/DELETE는
 * Hibernate {@code @Immutable} 로 차단된다).
 */
public interface UserActionLogRepository extends JpaRepository<UserActionLog, Long> {
}
