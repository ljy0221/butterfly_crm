package com.b101.butterfly.reporting.reconciliation.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.repository.NotificationCountSummary;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.reporting.reconciliation.dto.ReconciliationItem;
import com.b101.butterfly.reporting.reconciliation.entity.SendReconciliation;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionInfo;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionPort;
import com.b101.butterfly.reporting.reconciliation.repository.SendReconciliationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S14P31B101-126 코어 검증 로직 단위 테스트.
 *
 * <p>외부 의존(CampaignExecutionPort, NotificationLogRepository, SendReconciliationRepository)은
 * Mockito mock으로 stub. 도메인이 Port를 통해서만 외부 데이터에 접근한다는 격리 경계도
 * 본 테스트에서 강제된다.
 */
class ReconciliationServiceTest {

    private static final LocalDateTime EXECUTED_AT = LocalDateTime.of(2026, 4, 28, 10, 0, 0);

    private CampaignExecutionPort campaignExecutionPort;
    private ReportingNotificationLogRepository notificationLogRepository;
    private SendReconciliationRepository reconciliationRepository;
    private ReconciliationService service;

    @BeforeEach
    void setup() {
        campaignExecutionPort = mock(CampaignExecutionPort.class);
        notificationLogRepository = mock(ReportingNotificationLogRepository.class);
        reconciliationRepository = mock(SendReconciliationRepository.class);
        service = new ReconciliationService(
                campaignExecutionPort, notificationLogRepository, reconciliationRepository
        );

        // save가 인자를 그대로 반환하도록 stub
        when(reconciliationRepository.save(any(SendReconciliation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private CampaignExecutionInfo info(Channel channel, int targetCount) {
        return new CampaignExecutionInfo(99L, 4521L, 123L, channel, targetCount, EXECUTED_AT);
    }

    @Nested
    @DisplayName("reconcile(executionId)")
    class ReconcileById {

        @Test
        @DisplayName("외부 메타 미존재 → NoSuchElementException")
        void executionNotFound() {
            when(campaignExecutionPort.find(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reconcile(99L))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("reconcileInfo(info)")
    class ReconcileByInfo {

        @Test
        @DisplayName("일치 케이스 (expected == actual) → consistent=true, note=null")
        void consistent() {
            CampaignExecutionInfo info = info(Channel.PUSH, 1000);
            when(notificationLogRepository.countsForReconciliation(99L, Channel.PUSH))
                    .thenReturn(new NotificationCountSummary(1000L, 980L, 20L));

            SendReconciliation result = service.reconcileInfo(info);

            assertThat(result.getExpectedCount()).isEqualTo(1000);
            assertThat(result.getActualCount()).isEqualTo(1000);
            assertThat(result.getSentCount()).isEqualTo(980);
            assertThat(result.getFailedCount()).isEqualTo(20);
            assertThat(result.getDiffCount()).isZero();
            assertThat(result.isConsistent()).isTrue();
            assertThat(result.getNote()).isNull();
        }

        @Test
        @DisplayName("누락 케이스 (expected > actual) → consistent=false, note='N건 누락 감지'")
        void missingCount() {
            CampaignExecutionInfo info = info(Channel.PUSH, 1000);
            when(notificationLogRepository.countsForReconciliation(99L, Channel.PUSH))
                    .thenReturn(new NotificationCountSummary(998L, 970L, 28L));

            SendReconciliation result = service.reconcileInfo(info);

            assertThat(result.getDiffCount()).isEqualTo(2);
            assertThat(result.isConsistent()).isFalse();
            assertThat(result.getNote()).isEqualTo("2건 누락 감지");
        }

        @Test
        @DisplayName("초과 케이스 (expected < actual, 중복 가능성) → consistent=false, note='N건 초과 감지'")
        void overCount() {
            CampaignExecutionInfo info = info(Channel.EMAIL, 500);
            when(notificationLogRepository.countsForReconciliation(99L, Channel.EMAIL))
                    .thenReturn(new NotificationCountSummary(503L, 500L, 0L));

            SendReconciliation result = service.reconcileInfo(info);

            assertThat(result.getDiffCount()).isEqualTo(-3);
            assertThat(result.isConsistent()).isFalse();
            assertThat(result.getNote()).isEqualTo("3건 초과 감지");
        }

        @Test
        @DisplayName("저장된 row의 회차/채널/projectId/campaignId가 외부 메타 그대로 반영")
        void persistsCampaignContext() {
            CampaignExecutionInfo info = info(Channel.SMS, 50);
            when(notificationLogRepository.countsForReconciliation(99L, Channel.SMS))
                    .thenReturn(new NotificationCountSummary(50L, 50L, 0L));

            service.reconcileInfo(info);

            ArgumentCaptor<SendReconciliation> captor = ArgumentCaptor.forClass(SendReconciliation.class);
            org.mockito.Mockito.verify(reconciliationRepository).save(captor.capture());
            SendReconciliation saved = captor.getValue();

            assertThat(saved.getCampaignExecutionId()).isEqualTo(99L);
            assertThat(saved.getCampaignId()).isEqualTo(4521L);
            assertThat(saved.getProjectId()).isEqualTo(123L);
            assertThat(saved.getChannel()).isEqualTo(Channel.SMS);
            assertThat(saved.getCheckedAt()).isNotNull();
        }

        @Test
        @DisplayName("카운트 결과가 null이어도 0으로 처리되어 NPE 없음 (회차 데이터 0건 케이스)")
        void zeroCountsHandled() {
            CampaignExecutionInfo info = info(Channel.PUSH, 100);
            when(notificationLogRepository.countsForReconciliation(99L, Channel.PUSH))
                    .thenReturn(new NotificationCountSummary(0L, 0L, 0L));

            SendReconciliation result = service.reconcileInfo(info);

            assertThat(result.getActualCount()).isZero();
            assertThat(result.getDiffCount()).isEqualTo(100);
            assertThat(result.isConsistent()).isFalse();
            assertThat(result.getNote()).isEqualTo("100건 누락 감지");
        }
    }

    @Nested
    @DisplayName("reconcileCampaign(campaignId) — API #22 트리거")
    class ReconcileCampaign {

        @Test
        @DisplayName("회차 0건 캠페인 → 빈 리스트 (예외 아님)")
        void emptyCampaign() {
            when(campaignExecutionPort.findByCampaign(4521L)).thenReturn(List.of());

            List<ReconciliationItem> result = service.reconcileCampaign(4521L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("회차 N건 → 회차별로 reconcile 수행, 결과 N건 반환")
        void multipleExecutions() {
            CampaignExecutionInfo e1 = new CampaignExecutionInfo(11L, 4521L, 123L, Channel.PUSH, 100, EXECUTED_AT);
            CampaignExecutionInfo e2 = new CampaignExecutionInfo(12L, 4521L, 123L, Channel.EMAIL, 50, EXECUTED_AT);
            when(campaignExecutionPort.findByCampaign(4521L)).thenReturn(List.of(e1, e2));
            when(notificationLogRepository.countsForReconciliation(11L, Channel.PUSH))
                    .thenReturn(new NotificationCountSummary(100L, 100L, 0L));
            when(notificationLogRepository.countsForReconciliation(12L, Channel.EMAIL))
                    .thenReturn(new NotificationCountSummary(48L, 45L, 3L));

            List<ReconciliationItem> result = service.reconcileCampaign(4521L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).campaignExecutionId()).isEqualTo(11L);
            assertThat(result.get(0).consistent()).isTrue();
            assertThat(result.get(1).campaignExecutionId()).isEqualTo(12L);
            assertThat(result.get(1).consistent()).isFalse();
            assertThat(result.get(1).note()).isEqualTo("2건 누락 감지");
        }
    }

    @Nested
    @DisplayName("reconcileExecution(executionId) — API #23 트리거")
    class ReconcileExecution {

        @Test
        @DisplayName("회차 미존재 → BusinessException(EXECUTION_NOT_FOUND, 404)")
        void executionNotFound() {
            when(campaignExecutionPort.find(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reconcileExecution(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AnalyticsErrorCode.EXECUTION_NOT_FOUND);
        }

        @Test
        @DisplayName("회차 존재 → ReconciliationItem 반환 (consistent + 메타 반영)")
        void existingExecution() {
            CampaignExecutionInfo info = info(Channel.PUSH, 1000);
            when(campaignExecutionPort.find(99L)).thenReturn(Optional.of(info));
            when(notificationLogRepository.countsForReconciliation(99L, Channel.PUSH))
                    .thenReturn(new NotificationCountSummary(1000L, 1000L, 0L));

            ReconciliationItem result = service.reconcileExecution(99L);

            assertThat(result.campaignExecutionId()).isEqualTo(99L);
            assertThat(result.channel()).isEqualTo(Channel.PUSH);
            assertThat(result.expectedCount()).isEqualTo(1000);
            assertThat(result.consistent()).isTrue();
        }
    }
}
