package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.reporting.audit.service.AuditLogCsvExportService;
import com.b101.butterfly.reporting.export.CsvWriter;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelQuery;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelResponse;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelStageRow;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 캠페인 펀널 CSV 내보내기 서비스 (S14P31B101-91 — S14P31B101-90 후속 확장).
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link CampaignFunnelService#getFunnel}을 호출해 5단계 응답을 받는다 (검증·집계 위임).</li>
 *   <li>응답을 RFC 4180 이스케이프하여 응답 Writer에 출력 (BOM + 헤더 + 5 row).</li>
 *   <li>출력이 끝나면 {@code CAMPAIGN_FUNNEL_EXPORT} action으로 self-audit 이벤트를 발행한다.</li>
 * </ol>
 *
 * <p><b>scoped audit</b>: 행위자({@code actorMemberId})가 null이면 audit 이벤트를 발행하지 않는다 —
 * AuditLogCsvExportService와 동일한 방어. {@code AuditRecordedEvent.AFTER_COMMIT} listener를
 * 깨우려면 발행 자체가 트랜잭션 안에서 일어나야 하므로 본 메서드는 {@code @Transactional}로 감싼다.
 *
 * <p><b>BOM 상수</b>는 {@link AuditLogCsvExportService#UTF8_BOM}을 재사용한다 (DRY).
 *
 * <p><b>대용량 처리</b>: 펀널 응답은 정확히 5 row이므로 streaming/임계 처리가 필요 없다 —
 * 메모리에서 바로 직렬화한다. 같은 패턴이지만 audit 로그와 달리 row 수가 고정.
 */
@Service
@RequiredArgsConstructor
public class CampaignFunnelCsvExportService {

    private static final Logger log = LoggerFactory.getLogger(CampaignFunnelCsvExportService.class);

    /** export 자체를 audit하는 action 코드. */
    static final String EXPORT_ACTION = "CAMPAIGN_FUNNEL_EXPORT";

    /** audit detail의 targetType — 다른 reports export와 일관되게 "CampaignFunnel". */
    static final String EXPORT_TARGET_TYPE = "CampaignFunnel";

    private final CampaignFunnelService campaignFunnelService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 펀널 응답을 CSV로 응답 Writer에 출력한다.
     *
     * @return 출력한 row 수 (헤더 제외, 항상 5)
     */
    @Transactional
    public long exportToCsv(
            CampaignFunnelQuery query,
            Writer out,
            Long actorMemberId,
            String ipAddress,
            String userAgent
    ) {
        // 검증·집계는 기존 서비스에 위임 (range 검증, BusinessException 동일).
        CampaignFunnelResponse response = campaignFunnelService.getFunnel(query);

        try {
            out.write(AuditLogCsvExportService.UTF8_BOM);

            CsvWriter csv = new CsvWriter(out);
            csv.writeHeader(CampaignFunnelCsvRow.HEADERS);

            long written = 0;
            for (CampaignFunnelStageRow stageRow : response.stages()) {
                csv.writeRow(CampaignFunnelCsvRow.toRow(stageRow));
                written++;
            }
            csv.flush();

            publishExportAudit(actorMemberId, query, ipAddress, userAgent, written);
            return written;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write campaign funnel CSV", e);
        }
    }

    /**
     * 본 export 자체를 audit_logs에 기록한다. {@link AuditRecordedEvent}는 AFTER_COMMIT에
     * 처리되므로 호출자 메서드가 트랜잭션 안에 있어야 listener가 동작한다 ({@link Transactional}).
     */
    private void publishExportAudit(
            Long actorMemberId, CampaignFunnelQuery query,
            String ipAddress, String userAgent, long exportedCount
    ) {
        if (actorMemberId == null) {
            // 시스템/익명 호출은 audit 대상 외 (운영상 발생할 일이 없지만 방어).
            log.warn("campaign funnel export without actor — skipped self-audit");
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("filter", filterDetail(query));
        detail.put("exportedCount", exportedCount);

        AuditRecordedEvent event = AuditRecordedEvent.of(
                query.projectId(), actorMemberId,
                EXPORT_ACTION, EXPORT_TARGET_TYPE, query.projectId(),
                ipAddress, userAgent, detail
        );
        eventPublisher.publishEvent(event);
    }

    private static Map<String, Object> filterDetail(CampaignFunnelQuery q) {
        Map<String, Object> m = new HashMap<>();
        if (q.projectId() != null) m.put("projectId", q.projectId());
        if (q.from() != null) m.put("from", q.from().toString());
        if (q.to() != null) m.put("to", q.to().toString());
        if (q.campaignId() != null) m.put("campaignId", q.campaignId());
        if (q.channel() != null) m.put("channel", q.channel().name());
        return m;
    }
}
