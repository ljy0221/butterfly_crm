package com.b101.butterfly.tenant.repository;

import com.b101.butterfly.tenant.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByProjectId(Long projectId, Pageable pageable);

    Page<AuditLog> findAllByProjectIdAndAction(Long projectId, String action, Pageable pageable);
}
