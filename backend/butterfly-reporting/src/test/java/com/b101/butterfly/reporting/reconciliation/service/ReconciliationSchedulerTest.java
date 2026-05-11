package com.b101.butterfly.reporting.reconciliation.service;

import com.b101.butterfly.reporting.health.entity.HealthStatus;
import com.b101.butterfly.reporting.health.entity.SystemHealthSnapshot;
import com.b101.butterfly.reporting.health.repository.SystemHealthSnapshotRepository;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.reporting.reconciliation.entity.SendReconciliation;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionInfo;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S14P31B101-187 PR-2 자동 배치 단위 테스트.
 *
 * <p>caller-side filter 정책(이서연 합의 2026-05-08) 검증:
 * notificationLogs 카운트가 0인 회차는 스킵, 1 이상인 회차만 reconcileInfo 호출.
 *
 * <p>중간 회차 1건이 예외를 던져도 배치가 멈추지 않고 다음 회차 처리를 계속하는지도 검증.
 */
class ReconciliationSchedulerTest {

    private static final LocalDateTime EXECUTED_AT = LocalDateTime.of(2026, 5, 7, 10, 0, 0);
    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 5, 7, 2, 0, 0);

    private CampaignExecutionPort campaignExecutionPort;
    private ReportingNotificationLogRepository notificationLogRepository;
    private ReconciliationService reconciliationService;
    private SystemHealthSnapshotRepository healthSnapshotRepository;
    private ReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        campaignExecutionPort = mock(CampaignExecutionPort.class);
        notificationLogRepository = mock(ReportingNotificationLogRepository.class);
        reconciliationService = mock(ReconciliationService.class);
        healthSnapshotRepository = mock(SystemHealthSnapshotRepository.class);
        scheduler = new ReconciliationScheduler(
                campaignExecutionPort, notificationLogRepository, reconciliationService,
                healthSnapshotRepository
        );
        // @Value 기본값은 Spring 컨텍스트 없이 평가되지 않으므로 reflection 으로 주입
        injectField(scheduler, "warnThreshold", 10);
        injectField(scheduler, "critThreshold", 100);
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field f = ReconciliationScheduler.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private CampaignExecutionInfo info(long execId, Channel channel) {
        return new CampaignExecutionInfo(execId, 4521L, 123L, channel, 100, EXECUTED_AT);
    }

    private SendReconciliation savedRow(boolean consistent) {
        // 실제 빌더로 생성 — consistent 는 (expectedCount - actualCount == 0) 으로 결정됨
        return SendReconciliation.create()
                .projectId(123L)
                .campaignId(4521L)
                .campaignExecutionId(99L)
                .channel(Channel.PUSH)
                .expectedCount(100)
                .actualCount(consistent ? 100 : 95)
                .sentCount(consistent ? 100 : 90)
                .failedCount(consistent ? 0 : 5)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("발송 row 0건 회차는 reconcileInfo 호출 없이 skipped 카운트")
    void zeroNotificationLogsAreSkipped() {
        CampaignExecutionInfo notSent = info(11L, Channel.PUSH);
        when(campaignExecutionPort.findRecent(SINCE)).thenReturn(List.of(notSent));
        when(notificationLogRepository.countByCampaignExecutionIdAndChannel(11L, Channel.PUSH))
                .thenReturn(0L);

        int[] result = scheduler.runFor(SINCE);

        assertThat(result[0]).isZero();    // checked
        assertThat(result[1]).isZero();    // consistent
        assertThat(result[2]).isZero();    // inconsistent
        assertThat(result[3]).isEqualTo(1); // skipped
        assertThat(result[4]).isZero();    // failed
        verify(reconciliationService, never()).reconcileInfo(any());
    }

    @Test
    @DisplayName("발송 row 1+ 회차만 reconcileInfo 호출, consistent/inconsistent 분류")
    void onlyExecutedExecutionsAreReconciled() {
        CampaignExecutionInfo notSent = info(11L, Channel.PUSH);
        CampaignExecutionInfo sentOk = info(12L, Channel.PUSH);
        CampaignExecutionInfo sentMismatch = info(13L, Channel.EMAIL);
        when(campaignExecutionPort.findRecent(SINCE))
                .thenReturn(List.of(notSent, sentOk, sentMismatch));
        when(notificationLogRepository.countByCampaignExecutionIdAndChannel(11L, Channel.PUSH))
                .thenReturn(0L);
        when(notificationLogRepository.countByCampaignExecutionIdAndChannel(12L, Channel.PUSH))
                .thenReturn(100L);
        when(notificationLogRepository.countByCampaignExecutionIdAndChannel(13L, Channel.EMAIL))
                .thenReturn(95L);
        when(reconciliationService.reconcileInfo(eq(sentOk))).thenReturn(savedRow(true));
        when(reconciliationService.reconcileInfo(eq(sentMismatch))).thenReturn(savedRow(false));

        int[] result = scheduler.runFor(SINCE);

        assertThat(result[0]).isEqualTo(2); // checked
        assertThat(result[1]).isEqualTo(1); // consistent
        assertThat(result[2]).isEqualTo(1); // inconsistent
        assertThat(result[3]).isEqualTo(1); // skipped (notSent)
        assertThat(result[4]).isZero();     // failed
        verify(reconciliationService).reconcileInfo(sentOk);
        verify(reconciliationService).reconcileInfo(sentMismatch);
        verify(reconciliationService, never()).reconcileInfo(notSent);
    }

    @Test
    @DisplayName("회차 1건 예외 발생해도 배치 중단 없이 다음 회차 처리 + failed 카운트")
    void exceptionOnOneExecutionDoesNotAbortBatch() {
        CampaignExecutionInfo first = info(20L, Channel.PUSH);
        CampaignExecutionInfo second = info(21L, Channel.PUSH);
        when(campaignExecutionPort.findRecent(SINCE)).thenReturn(List.of(first, second));
        when(notificationLogRepository.countByCampaignExecutionIdAndChannel(any(), any()))
                .thenReturn(50L);
        when(reconciliationService.reconcileInfo(eq(first)))
                .thenThrow(new RuntimeException("DB timeout"));
        when(reconciliationService.reconcileInfo(eq(second))).thenReturn(savedRow(true));

        int[] result = scheduler.runFor(SINCE);

        assertThat(result[0]).isEqualTo(1); // checked (second 만)
        assertThat(result[1]).isEqualTo(1); // consistent
        assertThat(result[2]).isZero();     // inconsistent
        assertThat(result[3]).isZero();     // skipped
        assertThat(result[4]).isEqualTo(1); // failed (first)
        verify(reconciliationService, times(1)).reconcileInfo(first);
        verify(reconciliationService, times(1)).reconcileInfo(second);
    }

    @Test
    @DisplayName("외부 회차 0건이면 reconcileInfo 미호출, 모든 카운트 0 — 단 메트릭 0 row 는 적재")
    void emptyRecentReturnsAllZeros() {
        when(campaignExecutionPort.findRecent(SINCE)).thenReturn(List.of());

        int[] result = scheduler.runFor(SINCE);

        assertThat(result).containsExactly(0, 0, 0, 0, 0);
        verify(reconciliationService, never()).reconcileInfo(any());
        // 매 배치 1행 적재 — 0건도 측정값
        verify(healthSnapshotRepository, times(1)).save(any(SystemHealthSnapshot.class));
    }

    // --- system_health_snapshots 메트릭 적재 (S14P31B101-187 PR-3) ---

    @Test
    @DisplayName("inconsistent < warn 임계치 (10) → OK 상태 적재")
    void healthMetricOkWhenBelowWarn() {
        givenSimpleBatch(5, false); // 5 회차 모두 consistent 처리

        scheduler.runFor(SINCE);

        SystemHealthSnapshot saved = captureSavedSnapshot();
        assertThat(saved.getMetricName())
                .isEqualTo(ReconciliationScheduler.METRIC_RECONCILIATION_INCONSISTENT_COUNT);
        assertThat(saved.getMetricValue().intValue()).isZero();
        assertThat(saved.getStatus()).isEqualTo(HealthStatus.OK);
    }

    @Test
    @DisplayName("inconsistent >= warn (10) 이지만 < crit (100) → WARN 상태 적재")
    void healthMetricWarnWhenBetweenThresholds() {
        givenSimpleBatch(15, true); // 15 회차 모두 inconsistent

        scheduler.runFor(SINCE);

        SystemHealthSnapshot saved = captureSavedSnapshot();
        assertThat(saved.getMetricValue().intValue()).isEqualTo(15);
        assertThat(saved.getStatus()).isEqualTo(HealthStatus.WARN);
    }

    @Test
    @DisplayName("inconsistent >= crit (100) → CRITICAL 상태 적재")
    void healthMetricCriticalWhenAtOrAboveCrit() {
        givenSimpleBatch(100, true); // 100 회차 모두 inconsistent

        scheduler.runFor(SINCE);

        SystemHealthSnapshot saved = captureSavedSnapshot();
        assertThat(saved.getMetricValue().intValue()).isEqualTo(100);
        assertThat(saved.getStatus()).isEqualTo(HealthStatus.CRITICAL);
    }

    @Test
    @DisplayName("메트릭 적재 실패해도 정합성 배치 결과는 정상 반환")
    void healthMetricInsertFailureDoesNotPropagate() {
        givenSimpleBatch(1, true);
        when(healthSnapshotRepository.save(any(SystemHealthSnapshot.class)))
                .thenThrow(new RuntimeException("DB timeout"));

        int[] result = scheduler.runFor(SINCE);

        assertThat(result[2]).isEqualTo(1); // inconsistent 카운트는 정상 반환
        verify(healthSnapshotRepository, times(1)).save(any(SystemHealthSnapshot.class));
    }

    private void givenSimpleBatch(int count, boolean inconsistent) {
        // count 개의 회차를 모두 같은 결과로 처리
        java.util.List<CampaignExecutionInfo> infos = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            infos.add(info(1000L + i, Channel.PUSH));
        }
        when(campaignExecutionPort.findRecent(SINCE)).thenReturn(infos);
        when(notificationLogRepository.countByCampaignExecutionIdAndChannel(any(), any()))
                .thenReturn(50L);
        when(reconciliationService.reconcileInfo(any())).thenReturn(savedRow(!inconsistent));
    }

    private SystemHealthSnapshot captureSavedSnapshot() {
        ArgumentCaptor<SystemHealthSnapshot> captor = ArgumentCaptor.forClass(SystemHealthSnapshot.class);
        verify(healthSnapshotRepository).save(captor.capture());
        return captor.getValue();
    }
}
