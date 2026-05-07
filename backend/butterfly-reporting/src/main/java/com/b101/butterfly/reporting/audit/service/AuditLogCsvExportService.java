package com.b101.butterfly.reporting.audit.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.reporting.audit.dto.AuditLogQuery;
import com.b101.butterfly.reporting.audit.entity.AuditLog;
import com.b101.butterfly.reporting.audit.repository.AuditLogRepository;
import com.b101.butterfly.reporting.audit.repository.AuditLogSpecifications;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.b101.butterfly.reporting.export.CsvWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 감사 로그 CSV 내보내기 서비스 (S14P31B101-35 / S14P31B101-91 MVP).
 *
 * <p>흐름:
 * <ol>
 *   <li>요청 필터로 `audit_logs`를 페이지 단위로 streaming 조회 (메모리 보호).</li>
 *   <li>각 row를 RFC 4180 이스케이프하여 응답 Writer에 직접 출력.</li>
 *   <li>출력이 끝나면 행위자가 본 export를 수행했음을 {@link AuditRecordedEvent}로 발행
 *       — `AUDIT_LOG_EXPORT` action으로 audit_logs에 자체 기록.</li>
 * </ol>
 *
 * <p><b>스코프 (MVP)</b>: 동기 streaming 응답만 지원. 대용량 임계값 초과 시 202 Accepted +
 * 비동기 처리는 별도 티켓에서 다룬다 — 본 PR은 메모리 보호용 페이지 streaming + 행 수
 * 임계 초과 시 명시적 거부로 충분히 안전.
 *
 * <p><b>비밀값 보호</b>: 명세서 §"보안" — CSV 응답에 비밀 평문은 포함되지 않는다. {@code detail}
 * 컬럼은 도메인이 발행할 때 이미 SHA-256 해시 앞 8자만 담기로 합의되어 있으므로, 본 서비스는
 * 그대로 직렬화한다.
 */
@Service
@RequiredArgsConstructor
public class AuditLogCsvExportService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogCsvExportService.class);

    /** UTF-8 BOM — Excel 한글 인식용. 응답 본문 첫 바이트로 출력. */
    public static final String UTF8_BOM = "﻿";

    /** 한 페이지에 가져올 row 수. 메모리 ↔ DB 라운드트립 trade-off. */
    static final int STREAMING_PAGE_SIZE = 1000;

    /** 본 PR 범위에서 동기 응답으로 허용하는 최대 row 수. 초과 시 거부 (비동기 패턴은 후속). */
    static final long MAX_SYNC_EXPORT_ROWS = 50_000L;

    /** export 자체를 audit하는 action 코드. */
    static final String EXPORT_ACTION = "AUDIT_LOG_EXPORT";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /**
     * 필터에 매칭되는 audit_logs를 CSV로 응답 Writer에 streaming 출력한다.
     *
     * @return 출력한 row 수 (헤더 제외)
     */
    public long exportToCsv(
            AuditLogQuery query,
            Writer out,
            Long actorMemberId,
            String ipAddress,
            String userAgent
    ) {
        validate(query);

        Specification<AuditLog> spec = AuditLogSpecifications.withFilters(
                query.projectId(),
                query.memberId(),
                query.action(),
                query.targetType(),
                query.startDate(),
                query.endDate()
        );

        long total = auditLogRepository.count(spec);
        if (total > MAX_SYNC_EXPORT_ROWS) {
            throw new BusinessException(AnalyticsErrorCode.DATE_RANGE_TOO_LARGE);
        }

        try {
            out.write(UTF8_BOM);

            CsvWriter csv = new CsvWriter(out);
            csv.writeHeader(AuditLogCsvRow.HEADERS);

            long written = streamPages(spec, csv);
            csv.flush();

            publishExportAudit(actorMemberId, query, ipAddress, userAgent, written);
            return written;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write audit log CSV", e);
        }
    }

    private void validate(AuditLogQuery query) {
        if (query.startDate() != null && query.endDate() != null
                && query.startDate().isAfter(query.endDate())) {
            throw new BusinessException(AnalyticsErrorCode.INVALID_DATE_RANGE);
        }
    }

    /**
     * 페이지 단위로 끊어 가져와 CSV로 출력. 한 페이지가 끝나면 다음 페이지 fetch — 메모리에
     * 전체 결과를 올리지 않는다. 실제 page.hasNext()가 false가 되면 종료.
     */
    private long streamPages(Specification<AuditLog> spec, CsvWriter csv) throws IOException {
        long written = 0;
        int pageNumber = 0;
        while (true) {
            final int currentPage = pageNumber;
            // 트랜잭션은 페이지마다 짧게 — long-lived 읽기 트랜잭션 회피.
            Page<AuditLog> page = transactionTemplate.execute(status ->
                    auditLogRepository.findAll(spec,
                            PageRequest.of(currentPage, STREAMING_PAGE_SIZE,
                                    Sort.by(Sort.Direction.DESC, "createdAt"))));
            if (page == null || page.isEmpty()) {
                break;
            }
            for (AuditLog row : page.getContent()) {
                csv.writeRow(AuditLogCsvRow.toRow(row, objectMapper));
                written++;
            }
            if (!page.hasNext()) {
                break;
            }
            pageNumber++;
        }
        return written;
    }

    /**
     * 본 export 자체를 audit_logs에 기록한다. {@link AuditRecordedEvent}는 AFTER_COMMIT
     * 단계에 처리되므로, 본 메서드는 새 트랜잭션을 열어 commit해야 listener가 동작한다.
     */
    private void publishExportAudit(
            Long actorMemberId, AuditLogQuery query,
            String ipAddress, String userAgent, long exportedCount
    ) {
        if (actorMemberId == null) {
            // 시스템/익명 호출은 audit 대상 외 (운영상 발생할 일이 없지만 방어).
            log.warn("audit log export without actor — skipped self-audit");
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("filter", filterDetail(query));
        detail.put("exportedCount", exportedCount);

        AuditRecordedEvent event = AuditRecordedEvent.of(
                query.projectId(), actorMemberId,
                EXPORT_ACTION, "AuditLog", null,
                ipAddress, userAgent, detail
        );
        // AFTER_COMMIT listener를 깨우려면 발행 자체가 트랜잭션 안에서 일어나야 한다.
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private static Map<String, Object> filterDetail(AuditLogQuery q) {
        Map<String, Object> m = new HashMap<>();
        if (q.projectId() != null) m.put("projectId", q.projectId());
        if (q.memberId() != null) m.put("memberId", q.memberId());
        if (q.action() != null && !q.action().isBlank()) m.put("action", q.action());
        if (q.targetType() != null && !q.targetType().isBlank()) m.put("targetType", q.targetType());
        if (q.startDate() != null) m.put("startDate", q.startDate().toString());
        if (q.endDate() != null) m.put("endDate", q.endDate().toString());
        return m;
    }
}
