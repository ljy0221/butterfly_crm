package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.reporting.audit.service.AuditLogCsvExportService;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelQuery;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelResponse;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelStageRow;
import com.b101.butterfly.reporting.report.dto.FunnelStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CampaignFunnelCsvExportService 단위 테스트.
 *
 * <p>{@link CampaignFunnelService}는 mock해서 검증·집계가 위임되는지 확인하고,
 * 본 서비스는 BOM/헤더/row 직렬화 + self-audit 발행만 검증한다 — DB·트랜잭션 의존 없음.
 */
@DisplayName("CampaignFunnelCsvExportService — 펀널 CSV 직렬화 + CAMPAIGN_FUNNEL_EXPORT 자체 기록")
class CampaignFunnelCsvExportServiceTest {

    private CampaignFunnelService funnelService;
    private ApplicationEventPublisher eventPublisher;
    private CampaignFunnelCsvExportService service;

    @BeforeEach
    void setUp() {
        funnelService = mock(CampaignFunnelService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new CampaignFunnelCsvExportService(funnelService, eventPublisher);
    }

    private CampaignFunnelQuery query() {
        return new CampaignFunnelQuery(100L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                null, null);
    }

    private CampaignFunnelResponse fullResponse() {
        // 5단계 모두 채워진 정상 응답 (분모 0 없음).
        List<CampaignFunnelStageRow> stages = List.of(
                stage(FunnelStage.SENT, 1000, "100.00", "100.00", "0.00"),
                stage(FunnelStage.DELIVERED, 950, "95.00", "95.00", "5.00"),
                stage(FunnelStage.OPEN, 500, "50.00", "52.63", "47.37"),
                stage(FunnelStage.CLICK, 200, "20.00", "40.00", "60.00"),
                stage(FunnelStage.CONVERT, 50, "5.00", "25.00", "75.00")
        );
        return new CampaignFunnelResponse(100L, null, null,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                stages, FunnelStage.CONVERT);
    }

    private static CampaignFunnelStageRow stage(FunnelStage stage, long count,
                                                String fromStart, String fromPrev, String dropOff) {
        return new CampaignFunnelStageRow(stage, count,
                new BigDecimal(fromStart), new BigDecimal(fromPrev), new BigDecimal(dropOff));
    }

    @Test
    @DisplayName("정상 — BOM + 헤더 + 5 row 출력 + CAMPAIGN_FUNNEL_EXPORT 이벤트 발행")
    void writesBomHeaderRowsAndPublishesAudit() {
        CampaignFunnelQuery q = query();
        given(funnelService.getFunnel(any(CampaignFunnelQuery.class))).willReturn(fullResponse());

        StringWriter out = new StringWriter();
        long count = service.exportToCsv(q, out, 999L, "10.0.0.1", "test/ua");

        assertThat(count).isEqualTo(5);
        String csv = out.toString();

        // 1) UTF-8 BOM이 첫 바이트
        assertThat(csv.startsWith(AuditLogCsvExportService.UTF8_BOM)).isTrue();
        // 2) 헤더 row
        assertThat(csv).contains("stage,count,conversionFromStart,conversionFromPrev,dropOffFromPrev\n");
        // 3) 데이터 row 5개 — stage enum name + plain decimal
        assertThat(csv).contains("SENT,1000,100.00,100.00,0.00\n");
        assertThat(csv).contains("DELIVERED,950,95.00,95.00,5.00\n");
        assertThat(csv).contains("OPEN,500,50.00,52.63,47.37\n");
        assertThat(csv).contains("CLICK,200,20.00,40.00,60.00\n");
        assertThat(csv).contains("CONVERT,50,5.00,25.00,75.00\n");

        // 4) CAMPAIGN_FUNNEL_EXPORT 이벤트가 발행됐고 actor/IP/UA + filter detail이 들어감
        ArgumentCaptor<AuditRecordedEvent> captor = ArgumentCaptor.forClass(AuditRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AuditRecordedEvent ev = captor.getValue();
        assertThat(ev.action()).isEqualTo(CampaignFunnelCsvExportService.EXPORT_ACTION);
        assertThat(ev.actorId()).isEqualTo(999L);
        assertThat(ev.projectId()).isEqualTo(100L);
        assertThat(ev.targetType()).isEqualTo(CampaignFunnelCsvExportService.EXPORT_TARGET_TYPE);
        assertThat(ev.targetId()).isEqualTo(100L);
        assertThat(ev.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(ev.userAgent()).isEqualTo("test/ua");
        assertThat(ev.detail()).containsEntry("exportedCount", 5L);
        assertThat(ev.detail()).containsKey("filter");
    }

    @Test
    @DisplayName("비율 null (분모 0 등) — 빈 cell로 직렬화 (0%와 구분)")
    void serializesNullRatesAsEmpty() {
        // SENT 카운트 0인 케이스 — 직전 단계 통과율은 항상 100% (분모=자기 자신)이지만
        // 후속 단계는 분모가 0이라 모든 비율이 null이 된다.
        List<CampaignFunnelStageRow> stages = List.of(
                new CampaignFunnelStageRow(FunnelStage.SENT, 0, null, null, null),
                new CampaignFunnelStageRow(FunnelStage.DELIVERED, 0, null, null, null),
                new CampaignFunnelStageRow(FunnelStage.OPEN, 0, null, null, null),
                new CampaignFunnelStageRow(FunnelStage.CLICK, 0, null, null, null),
                new CampaignFunnelStageRow(FunnelStage.CONVERT, 0, null, null, null)
        );
        CampaignFunnelResponse zero = new CampaignFunnelResponse(100L, null, null,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), stages, null);
        given(funnelService.getFunnel(any(CampaignFunnelQuery.class))).willReturn(zero);

        StringWriter out = new StringWriter();
        service.exportToCsv(query(), out, 999L, "1.1.1.1", "ua");

        // 5개 비율 컬럼 모두 빈 cell — "stage,count,,,," 패턴
        assertThat(out.toString()).contains("SENT,0,,,\n");
        assertThat(out.toString()).contains("CONVERT,0,,,\n");
    }

    @Test
    @DisplayName("actor 없음(null) — self-audit 이벤트 발행 안 함 (방어)")
    void skipsSelfAuditWhenActorMissing() {
        given(funnelService.getFunnel(any(CampaignFunnelQuery.class))).willReturn(fullResponse());

        service.exportToCsv(query(), new StringWriter(), null, "1.1.1.1", "ua");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("actor 있음 — CAMPAIGN_FUNNEL_EXPORT 이벤트 1회 발행 (filter detail 포함)")
    void publishesExportAuditWithFilterDetail() {
        // 모든 옵셔널 필터를 채운 쿼리
        CampaignFunnelQuery q = new CampaignFunnelQuery(100L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                42L, Channel.EMAIL);
        given(funnelService.getFunnel(any(CampaignFunnelQuery.class))).willReturn(fullResponse());

        service.exportToCsv(q, new StringWriter(), 999L, "1.1.1.1", "ua");

        ArgumentCaptor<AuditRecordedEvent> captor = ArgumentCaptor.forClass(AuditRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AuditRecordedEvent ev = captor.getValue();

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> filter = (java.util.Map<String, Object>) ev.detail().get("filter");
        assertThat(filter).containsEntry("projectId", 100L);
        assertThat(filter).containsEntry("from", "2026-04-01");
        assertThat(filter).containsEntry("to", "2026-04-30");
        assertThat(filter).containsEntry("campaignId", 42L);
        assertThat(filter).containsEntry("channel", "EMAIL");
    }

    @Test
    @DisplayName("validate 위임 — funnel 서비스가 던지는 BusinessException은 그대로 전파")
    void delegatesValidationToFunnelService() {
        CampaignFunnelQuery q = query();
        given(funnelService.getFunnel(any(CampaignFunnelQuery.class)))
                .willThrow(new BusinessException(AnalyticsErrorCode.INVALID_DATE_RANGE));

        assertThatThrownBy(() -> service.exportToCsv(q, new StringWriter(), 999L, "1.1.1.1", "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AnalyticsErrorCode.INVALID_DATE_RANGE);

        // 검증 실패 시 self-audit도 발행되지 않아야 함
        verify(eventPublisher, never()).publishEvent(any());
    }
}
