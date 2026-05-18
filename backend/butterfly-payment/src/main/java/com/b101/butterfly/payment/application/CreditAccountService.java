package com.b101.butterfly.payment.application;

import com.b101.butterfly.payment.application.result.CreditBalanceResult;
import com.b101.butterfly.payment.application.result.CreditTransactionResult;
import com.b101.butterfly.payment.domain.CreditAccount;
import com.b101.butterfly.payment.domain.CreditAccountRepository;
import com.b101.butterfly.payment.domain.CreditReservation;
import com.b101.butterfly.payment.domain.CreditReservationRepository;
import com.b101.butterfly.payment.domain.CreditTransaction;
import com.b101.butterfly.payment.domain.CreditTransactionRepository;
import com.b101.butterfly.payment.domain.enums.CreditReason;
import com.b101.butterfly.payment.domain.enums.CreditReferenceType;
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
    private final CreditReservationRepository creditReservationRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    @Transactional(readOnly = true)
    public CreditBalanceResult getBalance(Long projectId) {
        return creditAccountRepository.findById(projectId)
                .map(account -> new CreditBalanceResult(
                        projectId,
                        account.getBalance(),
                        account.getReservedBalance(),
                        account.availableBalance()
                ))
                .orElseGet(() -> new CreditBalanceResult(projectId, 0L, 0L, 0L));
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

    /**
     * 캠페인 실행 회차의 예상 발송 비용을 예약한다.
     *
     * <p>같은 campaignExecutionId는 한 번만 예약된다. 프로젝트 잔액 row를 비관 락으로 잠근 뒤
     * availableBalance(balance - reservedBalance)를 검사하므로 여러 캠페인이 동시에 시작되어도 초과 예약되지 않는다.
     */
    @Transactional
    public void reserveForCampaignExecution(
            Long projectId,
            Long campaignId,
            Long campaignExecutionId,
            int expectedCount,
            long unitPrice
    ) {
        CreditReservation existing = creditReservationRepository
                .findByCampaignExecutionId(campaignExecutionId)
                .orElse(null);
        if (existing != null) {
            return;
        }

        CreditReservation reservation = CreditReservation.reserve(
                projectId, campaignId, campaignExecutionId, expectedCount, unitPrice);
        CreditAccount account = creditAccountRepository.findByProjectIdForUpdate(projectId)
                .orElseGet(() -> creditAccountRepository.save(CreditAccount.empty(projectId)));
        account.reserve(reservation.getReservedAmount());
        creditReservationRepository.save(reservation);
    }

    /**
     * 발송 결과를 기준으로 예약 크레딧을 실제 사용량으로 정산한다.
     *
     * <p>sentCount가 예약 대상 수보다 크면 같은 단가로 초과분을 추가 차감한다. 초과분은 다른 캠페인이
     * 예약한 reservedBalance를 침범하지 않고 availableBalance에서만 차감한다.
     */
    @Transactional
    public void settleCampaignExecution(Long campaignExecutionId, int sentCount) {
        CreditReservation reservation = creditReservationRepository
                .findByCampaignExecutionIdForUpdate(campaignExecutionId)
                .orElse(null);
        if (reservation == null || reservation.isClosed()) {
            return;
        }

        long actualAmount = sentCount * reservation.getUnitPrice();
        CreditAccount account = creditAccountRepository
                .findByProjectIdForUpdate(reservation.getProjectId())
                .orElseThrow();

        if (actualAmount == 0L) {
            account.releaseReserved(reservation.getReservedAmount());
            reservation.release();
            return;
        }

        long balanceAfter = account.settleReserved(
                reservation.getReservedAmount(),
                actualAmount
        );
        creditTransactionRepository.save(CreditTransaction.builder()
                .projectId(reservation.getProjectId())
                .referenceType(CreditReferenceType.CAMPAIGN_EXECUTION)
                .referenceId(campaignExecutionId)
                .delta(-actualAmount)
                .reason(CreditReason.SPEND)
                .balanceAfter(balanceAfter)
                .memo("캠페인 실행 크레딧 정산: campaignId=%d, sentCount=%d"
                        .formatted(reservation.getCampaignId(), sentCount))
                .build());
        reservation.settle(actualAmount);
    }
}
