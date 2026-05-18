package com.b101.butterfly.payment.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {

    /**
     * 비관 락(SELECT ... FOR UPDATE)으로 잔액 행을 잠그고 조회.
     *
     * <p>ADR-0019 §"멱등성 & 동시성" — Toss 호출 결과 받은 직후 짧은 트랜잭션 안에서 사용한다.
     * 외부 호출 중에 락을 보유하지 않도록 호출 순서에 주의.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreditAccount c WHERE c.projectId = :projectId")
    Optional<CreditAccount> findByProjectIdForUpdate(@Param("projectId") Long projectId);
}
