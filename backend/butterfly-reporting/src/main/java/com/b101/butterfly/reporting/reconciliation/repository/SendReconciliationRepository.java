package com.b101.butterfly.reporting.reconciliation.repository;

import com.b101.butterfly.reporting.reconciliation.entity.SendReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 발송 정합성 검증 결과 저장소. 동적 필터 조회는 {@link JpaSpecificationExecutor}로 처리하고,
 * 자주 쓰이는 "불일치 건만" 조회는 동일 메커니즘을 활용한 Specification 빌더(서비스 레이어)에서
 * 조립한다.
 */
public interface SendReconciliationRepository
        extends JpaRepository<SendReconciliation, Long>,
        JpaSpecificationExecutor<SendReconciliation> {
}
