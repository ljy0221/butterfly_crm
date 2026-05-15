package com.b101.butterfly.payment.application;

import com.b101.butterfly.payment.application.result.CreditBalanceResult;
import com.b101.butterfly.payment.application.result.CreditTransactionResult;
import com.b101.butterfly.payment.domain.CreditAccount;
import com.b101.butterfly.payment.domain.CreditAccountRepository;
import com.b101.butterfly.payment.domain.CreditTransaction;
import com.b101.butterfly.payment.domain.CreditTransactionRepository;
import com.b101.butterfly.payment.domain.enums.CreditReason;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크레딧 잔액·원장 서비스 (ADR-0019).
 *
 * <p>잔액 변동(append)은 항상 비관 락으로 행을 잠그고 ledger 와 동일 트랜잭션에서 발생한다.
 * {@link #creditFromPayment} 는 외부 PG 호출을 끝낸 직후의 짧은 트랜잭션 안에서만 호출돼야 한다.
 */
@Service
@RequiredArgsConstructor
public class CreditAccountService {

    private final CreditAccountRepository creditAccountRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    @Transactional(readOnly = true)
    public CreditBalanceResult getBalance(Long projectId) {
        long balance = creditAccountRepository.findById(projectId)
                .map(CreditAccount::getBalance)
                .orElse(0L);
        return new CreditBalanceResult(projectId, balance);
    }

    @Transactional(readOnly = true)
    public Page<CreditTransactionResult> listTransactions(Long projectId, Pageable pageable) {
        return creditTransactionRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
                .map(CreditTransactionResult::from);
    }

    /**
     * 결제 승인 → 잔액 적립 + 원장 append.
     *
     * <p>{@link Propagation#REQUIRES_NEW} 로 호출자 트랜잭션과 분리해 외부 호출 결과를 받은 직후의
     * 짧은 트랜잭션 경계를 보장한다. credit_account row 는 비관 락으로 잠그고 ledger UNIQUE
     * (payment_order_id, reason) 제약이 중복 적립을 막는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long creditFromPayment(Long projectId, Long paymentOrderId, long amount) {
        CreditAccount account = creditAccountRepository.findByProjectIdForUpdate(projectId)
                .orElseGet(() -> creditAccountRepository.save(CreditAccount.empty(projectId)));
        long balanceAfter = account.credit(amount);
        creditTransactionRepository.save(CreditTransaction.builder()
                .projectId(projectId)
                .paymentOrderId(paymentOrderId)
                .delta(amount)
                .reason(CreditReason.CHARGE)
                .balanceAfter(balanceAfter)
                .build());
        return balanceAfter;
    }
}
