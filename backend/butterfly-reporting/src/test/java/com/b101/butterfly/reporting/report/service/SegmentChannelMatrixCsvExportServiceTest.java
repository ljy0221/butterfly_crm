package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.reporting.audit.service.AuditLogCsvExportService;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixQuery;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixResponse;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SegmentChannelMatrixCsvExportService 단위 테스트.
 *
 * <p>{@link SegmentChannelMatrixService}는 mock해서 검증·집계가 위임되는지 확인하고,
 * 본 서비스는 BOM/헤더/row 직렬화 + self-audit 발행만 검증한다 — DB·트랜잭션 의존 없음.
 */
@DisplayName("SegmentChannelMatrixCsvExportService — 매트릭스 CSV 직렬화 + SEGMENT_CHANNEL_MATRIX_EXPORT 자체 기록")
class SegmentChannelMatrixCsvExportServiceTest {

    private SegmentChannelMatrixService matrixService;
    private ApplicationEventPublisher eventPublisher;
    private SegmentChannelMatrixCsvExportService service;

    @BeforeEach
    void setUp() {
        matrixService = mock(SegmentChannelMatrixService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new SegmentChannelMatrixCsvExportService(matrixService, eventPublisher);
    }

    private SegmentChannelMatrixQuery query() {
        return new SegmentChannelMatrixQuery(
                100L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                null, null, null, false
        );
    }

    private SegmentChannelMatrixResponse twoRowResponse() {
        SegmentChannelMatrixRow vipPush = new SegmentChannelMatrixRow(
                10L, "VIP", Channel.PUSH,
                1500, 1200, 50, 600, 150, 75,
                new BigDecimal("80.00"),
                new BigDecimal("50.00"),
                new BigDecimal("25.00"),
                new BigDecimal("5.00")
        );
        SegmentChannelMatrixRow newEmail = new SegmentChannelMatrixRow(
                20L, "NEW", Channel.EMAIL,
                500, 450, 10, 90, 20, 5,
                new BigDecimal("90.00"),
                new BigDecimal("20.00"),
                new BigDecimal("22.22"),
                new BigDecimal("1.00")
        );
        return new SegmentChannelMatrixResponse(
                100L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                List.of(vipPush, newEmail),
                new SegmentChannelMatrixResponse.Highlights(vipPush, vipPush, vipPush, newEmail)
        );
    }

    @Test
    @DisplayName("정상 — BOM + 헤더 + N row 출력 + SEGMENT_CHANNEL_MATRIX_EXPORT 이벤트 발행")
    void writesBomHeaderRowsAndPublishesAudit() {
        SegmentChannelMatrixQuery q = query();
        given(matrixService.getMatrix(any(SegmentChannelMatrixQuery.class)))
                .willReturn(twoRowResponse());

        StringWriter out = new StringWriter();
        long count = service.exportToCsv(q, out, 999L, "10.0.0.1", "test/ua");

        assertThat(count).isEqualTo(2);
        String csv = out.toString();

        // 1) UTF-8 BOM이 첫 바이트
        assertThat(csv.startsWith(AuditLogCsvExportService.UTF8_BOM)).isTrue();
        // 2) 헤더 row
        assertThat(csv).contains(
                "segmentId,segmentName,channel,sentCount,deliveredCount,failedCount,"
                        + "openCount,clickCount,convertCount,deliveryRate,openRate,clickRate,convertRate\n"
        );
        // 3) 데이터 row 2개 — segmentId/segmentName + channel name + plain decimal
        assertThat(csv).contains("10,VIP,PUSH,1500,1200,50,600,150,75,80.00,50.00,25.00,5.00\n");
        assertThat(csv).contains("20,NEW,EMAIL,500,450,10,90,20,5,90.00,20.00,22.22,1.00\n");

        // 4) SEGMENT_CHANNEL_MATRIX_EXPORT 이벤트가 발행됐고 actor/IP/UA + filter detail이 들어감
        ArgumentCaptor<AuditRecordedEvent> captor = ArgumentCaptor.forClass(AuditRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AuditRecordedEvent ev = captor.getValue();
        assertThat(ev.action()).isEqualTo(SegmentChannelMatrixCsvExportService.EXPORT_ACTION);
        assertThat(ev.actorId()).isEqualTo(999L);
        assertThat(ev.projectId()).isEqualTo(100L);
        assertThat(ev.targetType()).isEqualTo(SegmentChannelMatrixCsvExportService.EXPORT_TARGET_TYPE);
        assertThat(ev.targetId()).isEqualTo(100L);
        assertThat(ev.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(ev.userAgent()).isEqualTo("test/ua");
        assertThat(ev.detail()).containsEntry("exportedCount", 2L);
        assertThat(ev.detail()).containsKey("filter");
    }

    @Test
    @DisplayName("비율 null (분모 0 등) — 빈 cell로 직렬화 (0%와 구분)")
    void serializesNullRatesAsEmpty() {
        // 카운트 0인 그룹 — 모든 비율이 null
        SegmentChannelMatrixRow zeroRow = new SegmentChannelMatrixRow(
                10L, "VIP", Channel.PUSH,
                0, 0, 0, 0, 0, 0,
                null, null, null, null
        );
        SegmentChannelMatrixResponse zero = new SegmentChannelMatrixResponse(
                100L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                List.of(zeroRow),
                new SegmentChannelMatrixResponse.Highlights(null, null, null, null)
        );
        given(matrixService.getMatrix(any(SegmentChannelMatrixQuery.class))).willReturn(zero);

        StringWriter out = new StringWriter();
        service.exportToCsv(query(), out, 999L, "1.1.1.1", "ua");

        // 4개 비율 컬럼이 모두 빈 cell — 끝에 ",,,," 패턴
        assertThat(out.toString()).contains("10,VIP,PUSH,0,0,0,0,0,0,,,,\n");
    }

    @Test
    @DisplayName("segmentId/segmentName이 null인 row (미지정 캠페인 그룹) — 빈 cell")
    void serializesNullSegmentAsEmpty() {
        SegmentChannelMatrixRow unsegmented = new SegmentChannelMatrixRow(
                null, null, Channel.SMS,
                100, 90, 0, 50, 10, 5,
                new BigDecimal("90.00"),
                new BigDecimal("55.56"),
                new BigDecimal("20.00"),
                new BigDecimal("5.00")
        );
        SegmentChannelMatrixResponse res = new SegmentChannelMatrixResponse(
                100L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                List.of(unsegmented),
                new SegmentChannelMatrixResponse.Highlights(unsegmented, unsegmented, unsegmented, unsegmented)
        );
        given(matrixService.getMatrix(any(SegmentChannelMatrixQuery.class))).willReturn(res);

        StringWriter out = new StringWriter();
        service.exportToCsv(query(), out, 999L, "1.1.1.1", "ua");

        // segmentId, segmentName 자리는 빈 cell — "" "" 두 개가 row 시작에서 비어 있어야 함
        assertThat(out.toString()).contains(",,SMS,100,90,0,50,10,5,90.00,55.56,20.00,5.00\n");
    }

    @Test
    @DisplayName("actor 없음(null) — self-audit 이벤트 발행 안 함 (방어)")
    void skipsSelfAuditWhenActorMissing() {
        given(matrixService.getMatrix(any(SegmentChannelMatrixQuery.class)))
                .willReturn(twoRowResponse());

        service.exportToCsv(query(), new StringWriter(), null, "1.1.1.1", "ua");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("actor 있음 — SEGMENT_CHANNEL_MATRIX_EXPORT 이벤트 1회 발행 (filter detail 포함)")
    void publishesExportAuditWithFilterDetail() {
        // 모든 옵셔널 필터를 채운 쿼리
        SegmentChannelMatrixQuery q = new SegmentChannelMatrixQuery(
                100L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                Set.of(Channel.EMAIL, Channel.PUSH),
                Set.of(10L, 20L),
                SegmentChannelMatrixQuery.SortBy.OPEN_RATE,
                true
        );
        given(matrixService.getMatrix(any(SegmentChannelMatrixQuery.class)))
                .willReturn(twoRowResponse());

        service.exportToCsv(q, new StringWriter(), 999L, "1.1.1.1", "ua");

        ArgumentCaptor<AuditRecordedEvent> captor = ArgumentCaptor.forClass(AuditRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AuditRecordedEvent ev = captor.getValue();

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> filter = (java.util.Map<String, Object>) ev.detail().get("filter");
        assertThat(filter).containsEntry("projectId", 100L);
        assertThat(filter).containsEntry("from", "2026-04-01");
        assertThat(filter).containsEntry("to", "2026-04-30");
        assertThat(filter).containsEntry("sortBy", "OPEN_RATE");
        assertThat(filter).containsEntry("ascending", true);

        // channels는 List<String> 형태로, 순서 무관
        @SuppressWarnings("unchecked")
        List<String> channels = (List<String>) filter.get("channels");
        assertThat(channels).containsExactlyInAnyOrder("EMAIL", "PUSH");

        // segmentIds는 정렬된 List<Long>
        @SuppressWarnings("unchecked")
        List<Long> segmentIds = (List<Long>) filter.get("segmentIds");
        assertThat(segmentIds).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("validate 위임 — matrix 서비스가 던지는 BusinessException은 그대로 전파")
    void delegatesValidationToMatrixService() {
        SegmentChannelMatrixQuery q = query();
        given(matrixService.getMatrix(any(SegmentChannelMatrixQuery.class)))
                .willThrow(new BusinessException(AnalyticsErrorCode.INVALID_DATE_RANGE));

        assertThatThrownBy(() -> service.exportToCsv(q, new StringWriter(), 999L, "1.1.1.1", "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AnalyticsErrorCode.INVALID_DATE_RANGE);

        // 검증 실패 시 self-audit도 발행되지 않아야 함
        verify(eventPublisher, never()).publishEvent(any());
    }
}
