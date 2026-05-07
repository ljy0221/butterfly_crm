package com.b101.butterfly.reporting.audit.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.reporting.audit.dto.AuditLogQuery;
import com.b101.butterfly.reporting.audit.entity.AuditLog;
import com.b101.butterfly.reporting.audit.repository.AuditLogRepository;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuditLogCsvExportService — RFC 4180 streaming + AUDIT_LOG_EXPORT 자체 기록")
class AuditLogCsvExportServiceTest {

    private AuditLogRepository repository;
    private ObjectMapper objectMapper;
    private ApplicationEventPublisher eventPublisher;
    private TransactionTemplate transactionTemplate;
    private AuditLogCsvExportService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        objectMapper = new ObjectMapper();
        eventPublisher = mock(ApplicationEventPublisher.class);
        // TransactionTemplate은 final 메서드가 많아 mock 어려움 — execute가 내부 콜백을 즉시 호출하는
        // 단순 fake로 구성. 본 테스트는 트랜잭션 자체가 아니라 콜백 실행 결과만 검증.
        transactionTemplate = fakeTransactionTemplate();
        service = new AuditLogCsvExportService(
                repository, objectMapper, eventPublisher, transactionTemplate);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TransactionTemplate fakeTransactionTemplate() {
        TransactionTemplate t = mock(TransactionTemplate.class);
        when(t.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        // executeWithoutResult는 Consumer<TransactionStatus>를 받는다 — 직접 accept 호출.
        org.mockito.Mockito.doAnswer(inv -> {
            java.util.function.Consumer consumer = inv.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(t).executeWithoutResult(any());
        return t;
    }

    private AuditLog auditLog(long id, Long projectId, long memberId,
                              String action, String targetType, Long targetId,
                              Map<String, Object> detail) {
        AuditLog log = AuditLog.create(projectId, memberId, action, targetType, targetId,
                detail, "127.0.0.1", "test-agent");
        // id, createdAt은 protected이므로 reflection으로 채움 — 테스트에서만.
        try {
            Field idF = AuditLog.class.getDeclaredField("id");
            idF.setAccessible(true);
            idF.set(log, id);
            Field tsF = AuditLog.class.getDeclaredField("createdAt");
            tsF.setAccessible(true);
            tsF.set(log, LocalDateTime.of(2026, 5, 1, 12, 0, 0));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return log;
    }

    @Test
    @DisplayName("from > to → INVALID_DATE_RANGE")
    void rejectsInvertedRange() {
        AuditLogQuery q = new AuditLogQuery(1L, null, null, null,
                LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1));
        assertThatThrownBy(() -> service.exportToCsv(q, new StringWriter(), 10L, "1.1.1.1", "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AnalyticsErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("매칭 row > 50,000 → DATE_RANGE_TOO_LARGE")
    void rejectsTooManyRows() {
        AuditLogQuery q = new AuditLogQuery(null, null, null, null, null, null);
        given(repository.count(any(Specification.class))).willReturn(50_001L);

        assertThatThrownBy(() -> service.exportToCsv(q, new StringWriter(), 10L, "1.1.1.1", "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AnalyticsErrorCode.DATE_RANGE_TOO_LARGE);
    }

    @Test
    @DisplayName("정상 — BOM + 헤더 + row 출력 + AUDIT_LOG_EXPORT 이벤트 발행")
    void writesBomHeaderRowsAndPublishesAudit() {
        AuditLog log1 = auditLog(1L, 100L, 200L, "FCM_KEY_UPDATE", "Project", 100L,
                Map.of("hashPrefix", "a1b2c3d4"));
        AuditLogQuery q = new AuditLogQuery(100L, null, null, null, null, null);
        given(repository.count(any(Specification.class))).willReturn(1L);
        given(repository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(log1)));

        StringWriter out = new StringWriter();
        long count = service.exportToCsv(q, out, 999L, "10.0.0.1", "test/ua");

        assertThat(count).isEqualTo(1);
        String csv = out.toString();

        // 1) UTF-8 BOM이 첫 바이트
        assertThat(csv.startsWith(AuditLogCsvExportService.UTF8_BOM)).isTrue();
        // 2) 헤더 row
        assertThat(csv).contains("auditId,projectId,memberId,action,targetType,targetId,"
                + "ipAddress,userAgent,createdAt,detail\n");
        // 3) 데이터 row — JSON detail은 따옴표 escape됨
        assertThat(csv).contains("1,100,200,FCM_KEY_UPDATE,Project,100,127.0.0.1,test-agent,"
                + "2026-05-01T12:00:00,\"{\"\"hashPrefix\"\":\"\"a1b2c3d4\"\"}\"\n");

        // 4) AUDIT_LOG_EXPORT 이벤트가 발행됐고 actor/IP/UA가 그대로 들어감
        ArgumentCaptor<AuditRecordedEvent> captor = ArgumentCaptor.forClass(AuditRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AuditRecordedEvent ev = captor.getValue();
        assertThat(ev.action()).isEqualTo(AuditLogCsvExportService.EXPORT_ACTION);
        assertThat(ev.actorId()).isEqualTo(999L);
        assertThat(ev.projectId()).isEqualTo(100L);
        assertThat(ev.targetType()).isEqualTo("AuditLog");
        assertThat(ev.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(ev.userAgent()).isEqualTo("test/ua");
        assertThat(ev.detail()).containsEntry("exportedCount", 1L);
        assertThat(ev.detail()).containsKey("filter");
    }

    @Test
    @DisplayName("쉼표/따옴표 포함 user_agent도 RFC 4180 이스케이프되어 출력")
    void escapesSpecialCharsInRow() {
        AuditLog log1 = auditLog(2L, null, 200L, "API_KEY_REVOKE", "ApiKey", 5L, null);
        // user-agent를 쉼표 포함으로 덮어쓰기
        try {
            Field uaF = AuditLog.class.getDeclaredField("userAgent");
            uaF.setAccessible(true);
            uaF.set(log1, "Mozilla/5.0, (Linux)");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        AuditLogQuery q = new AuditLogQuery(null, null, null, null, null, null);
        given(repository.count(any(Specification.class))).willReturn(1L);
        given(repository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(log1)));

        StringWriter out = new StringWriter();
        service.exportToCsv(q, out, 999L, "1.1.1.1", "ua");

        assertThat(out.toString()).contains("\"Mozilla/5.0, (Linux)\"");
    }

    @Test
    @DisplayName("actorMemberId == null — audit 이벤트 발행 안 함 (방어)")
    void skipsSelfAuditWhenActorMissing() {
        AuditLogQuery q = new AuditLogQuery(null, null, null, null, null, null);
        given(repository.count(any(Specification.class))).willReturn(0L);
        given(repository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        service.exportToCsv(q, new StringWriter(), null, "1.1.1.1", "ua");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("페이지 streaming — 1000건 + 1건 = 2 페이지 모두 fetch")
    void streamsPagesUntilExhausted() {
        // 첫 페이지 1000개 + 두 번째 페이지 1개
        List<AuditLog> page1Content = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            page1Content.add(auditLog(i, 1L, 1L, "X", "Y", null, null));
        }
        Page<AuditLog> page1 = new PageImpl<>(page1Content,
                org.springframework.data.domain.PageRequest.of(0, 1000), 1001);
        Page<AuditLog> page2 = new PageImpl<>(
                List.of(auditLog(1000, 1L, 1L, "X", "Y", null, null)),
                org.springframework.data.domain.PageRequest.of(1, 1000), 1001);

        AuditLogQuery q = new AuditLogQuery(null, null, null, null, null, null);
        given(repository.count(any(Specification.class))).willReturn(1001L);
        given(repository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(page1, page2);

        StringWriter out = new StringWriter();
        long written = service.exportToCsv(q, out, 999L, "1.1.1.1", "ua");

        assertThat(written).isEqualTo(1001L);
        // 두 번째 페이지까지 호출됐는지 검증
        verify(repository, org.mockito.Mockito.times(2))
                .findAll(any(Specification.class), any(Pageable.class));
    }
}
