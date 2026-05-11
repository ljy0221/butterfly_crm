package com.b101.butterfly.reporting.health.repository;

import com.b101.butterfly.reporting.health.entity.SystemHealthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link SystemHealthSnapshot} 저장소.
 *
 * <p>본 PR(187 PR-3) 범위에서는 INSERT-only 사용. 조회 API 는 별도 후속에서 추가.
 */
public interface SystemHealthSnapshotRepository extends JpaRepository<SystemHealthSnapshot, Long> {
}
