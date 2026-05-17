package com.b101.butterfly.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.payment.domain.CreditAccount;
import com.b101.butterfly.payment.domain.CreditAccountRepository;
import com.b101.butterfly.payment.domain.CreditReservation;
import com.b101.butterfly.payment.domain.CreditReservationRepository;
import com.b101.butterfly.payment.domain.CreditTransaction;
import com.b101.butterfly.payment.domain.CreditTransactionRepository;
import com.b101.butterfly.payment.domain.enums.CreditReason;
import com.b101.butterfly.payment.domain.enums.CreditReferenceType;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditAccountServiceTest {

    @Mock private CreditAccountRepository creditAccountRepository;
    @Mock private CreditReservationRepository creditReservationRepository;
    @Mock private CreditTransactionRepository creditTransactionRepository;

    private CreditAccountService creditAccountService;

    @BeforeEach
    void setUp() {
        creditAccountService = new CreditAccountService(
                creditAccountRepository,
                creditReservationRepository,
                creditTransactionRepository
        );
    }

    @Test
    void reserveForCampaignExecution_은_사용가능_잔액을_예약한다() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(10_000L);
        when(creditReservationRepository.findByCampaignExecutionId(30L))
                .thenReturn(Optional.empty());
        when(creditAccountRepository.findByProjectIdForUpdate(1L))
                .thenReturn(Optional.of(account));

        creditAccountService.reserveForCampaignExecution(1L, 20L, 30L, 7, 1_000L);

        assertThat(account.getReservedBalance()).isEqualTo(7_000L);
        ArgumentCaptor<CreditReservation> captor =
                ArgumentCaptor.forClass(CreditReservation.class);
        verify(creditReservationRepository).save(captor.capture());
        assertThat(captor.getValue().getReservedAmount()).isEqualTo(7_000L);
    }

    @Test
    void reserveForCampaignExecution_은_이미_예약된_실행이면_멱등_반환한다() {
        CreditReservation reservation = CreditReservation.reserve(1L, 20L, 30L, 7, 1_000L);
        when(creditReservationRepository.findByCampaignExecutionId(30L))
                .thenReturn(Optional.of(reservation));

        creditAccountService.reserveForCampaignExecution(1L, 20L, 30L, 7, 1_000L);

        verify(creditAccountRepository, never()).findByProjectIdForUpdate(any());
        verify(creditReservationRepository, never()).save(any());
    }

    @Test
    void reserveForCampaignExecution_은_잔액이_부족하면_PAY_INSUFFICIENT_BALANCE() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(5_000L);
        when(creditReservationRepository.findByCampaignExecutionId(30L))
                .thenReturn(Optional.empty());
        when(creditAccountRepository.findByProjectIdForUpdate(1L))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() ->
                creditAccountService.reserveForCampaignExecution(1L, 20L, 30L, 7, 1_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_INSUFFICIENT_BALANCE);

        verify(creditReservationRepository, never()).save(any());
    }

    @Test
    void reserveForCampaignExecution_은_여러_캠페인의_예약_잔액을_침범하지_않는다() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(10_000L);
        when(creditReservationRepository.findByCampaignExecutionId(30L))
                .thenReturn(Optional.empty());
        when(creditReservationRepository.findByCampaignExecutionId(31L))
                .thenReturn(Optional.empty());
        when(creditAccountRepository.findByProjectIdForUpdate(1L))
                .thenReturn(Optional.of(account));

        creditAccountService.reserveForCampaignExecution(1L, 20L, 30L, 6, 1_000L);

        assertThatThrownBy(() ->
                creditAccountService.reserveForCampaignExecution(1L, 21L, 31L, 5, 1_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_INSUFFICIENT_BALANCE);
        assertThat(account.getBalance()).isEqualTo(10_000L);
        assertThat(account.getReservedBalance()).isEqualTo(6_000L);
        verify(creditReservationRepository).save(any());
    }

    @Test
    void settleCampaignExecution_은_sentCount_기준으로_SPEND_원장을_남긴다() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(10_000L);
        account.reserve(7_000L);
        CreditReservation reservation = CreditReservation.reserve(1L, 20L, 30L, 7, 1_000L);
        when(creditReservationRepository.findByCampaignExecutionIdForUpdate(30L))
                .thenReturn(Optional.of(reservation));
        when(creditAccountRepository.findByProjectIdForUpdate(1L))
                .thenReturn(Optional.of(account));

        creditAccountService.settleCampaignExecution(30L, 5);

        assertThat(account.getBalance()).isEqualTo(5_000L);
        assertThat(account.getReservedBalance()).isZero();
        assertThat(reservation.getSettledAmount()).isEqualTo(5_000L);
        assertThat(reservation.getReleasedAmount()).isEqualTo(2_000L);

        ArgumentCaptor<CreditTransaction> captor =
                ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(captor.capture());
        CreditTransaction tx = captor.getValue();
        assertThat(tx.getDelta()).isEqualTo(-5_000L);
        assertThat(tx.getReason()).isEqualTo(CreditReason.SPEND);
        assertThat(tx.getReferenceType()).isEqualTo(CreditReferenceType.CAMPAIGN_EXECUTION);
        assertThat(tx.getReferenceId()).isEqualTo(30L);
    }

    @Test
    void settleCampaignExecution_은_예상보다_더_발송되면_추가분을_사용가능_잔액에서_차감한다() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(10_000L);
        account.reserve(7_000L);
        CreditReservation reservation = CreditReservation.reserve(1L, 20L, 30L, 7, 1_000L);
        when(creditReservationRepository.findByCampaignExecutionIdForUpdate(30L))
                .thenReturn(Optional.of(reservation));
        when(creditAccountRepository.findByProjectIdForUpdate(1L))
                .thenReturn(Optional.of(account));

        creditAccountService.settleCampaignExecution(30L, 8);

        assertThat(account.getBalance()).isEqualTo(2_000L);
        assertThat(account.getReservedBalance()).isZero();
        assertThat(reservation.getSettledAmount()).isEqualTo(8_000L);
        assertThat(reservation.getReleasedAmount()).isZero();

        ArgumentCaptor<CreditTransaction> captor =
                ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getDelta()).isEqualTo(-8_000L);
    }

    @Test
    void settleCampaignExecution_은_초과_발송분을_충당할_사용가능_잔액이_없으면_실패한다() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(7_000L);
        account.reserve(7_000L);
        CreditReservation reservation = CreditReservation.reserve(1L, 20L, 30L, 7, 1_000L);
        when(creditReservationRepository.findByCampaignExecutionIdForUpdate(30L))
                .thenReturn(Optional.of(reservation));
        when(creditAccountRepository.findByProjectIdForUpdate(1L))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> creditAccountService.settleCampaignExecution(30L, 8))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_INSUFFICIENT_BALANCE);
        assertThat(account.getBalance()).isEqualTo(7_000L);
        assertThat(account.getReservedBalance()).isEqualTo(7_000L);
        verify(creditTransactionRepository, never()).save(any());
    }

    @Test
    void settleCampaignExecution_은_sentCount_0이면_예약만_해제한다() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(10_000L);
        account.reserve(7_000L);
        CreditReservation reservation = CreditReservation.reserve(1L, 20L, 30L, 7, 1_000L);
        when(creditReservationRepository.findByCampaignExecutionIdForUpdate(30L))
                .thenReturn(Optional.of(reservation));
        when(creditAccountRepository.findByProjectIdForUpdate(1L))
                .thenReturn(Optional.of(account));

        creditAccountService.settleCampaignExecution(30L, 0);

        assertThat(account.getBalance()).isEqualTo(10_000L);
        assertThat(account.getReservedBalance()).isZero();
        assertThat(reservation.getReleasedAmount()).isEqualTo(7_000L);
        verify(creditTransactionRepository, never()).save(any());
    }

    @Test
    void settleCampaignExecution_은_예약이_없으면_멱등_반환한다() {
        when(creditReservationRepository.findByCampaignExecutionIdForUpdate(30L))
                .thenReturn(Optional.empty());

        creditAccountService.settleCampaignExecution(30L, 5);

        verify(creditAccountRepository, never()).findByProjectIdForUpdate(any());
        verify(creditTransactionRepository, never()).save(any());
    }

    @Test
    void settleCampaignExecution_은_이미_닫힌_예약이면_멱등_반환한다() {
        CreditReservation reservation = CreditReservation.reserve(1L, 20L, 30L, 7, 1_000L);
        reservation.settle(5_000L);
        when(creditReservationRepository.findByCampaignExecutionIdForUpdate(30L))
                .thenReturn(Optional.of(reservation));

        creditAccountService.settleCampaignExecution(30L, 5);

        verify(creditAccountRepository, never()).findByProjectIdForUpdate(any());
        verify(creditTransactionRepository, never()).save(any());
    }
}
