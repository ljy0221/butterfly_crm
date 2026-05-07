package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.reporting.audit.service.AuditLogCsvExportService;
import com.b101.butterfly.reporting.export.CsvWriter;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixQuery;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixResponse;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixRow;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Segment × Channel 매트릭스 CSV 내보내기 서비스 (S14P31B101-91 / S14P31B101-92 후속).
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link SegmentChannelMatrixService#getMatrix}을 호출해 매트릭스 응답을 받는다 (검증·집계 위임).</li>
 *   <li>응답 rows를 RFC 4180 이스케이프하여 응답 Writer에 출력 (BOM + 헤더 + N row).</li>
 *   <li>출력이 끝나면 {@code SEGMENT_CHANNEL_MATRIX_EXPORT} action으로 self-audit 이벤트를 발행한다.</li>
 * </ol>
 *
 * <p><b>scoped audit</b>: 행위자({@code actorMemberId})가 null이면 audit 이벤트를 발행하지 않는다 —
 * AuditLogCsvExportService / CampaignFunnelCsvExportService와 동일한 방어. AFTER_COMMIT
 * listener를 깨우려면 발행이 트랜잭션 안에서 일어나야 하므로 {@code @Transactional}로 감싼다.
 *
 * <p><b>BOM 상수</b>는 {@link AuditLogCsvExportService#UTF8_BOM}을 재사용한다 (DRY).
 *
 * <p><b>대용량 처리</b>: 매트릭스 응답 row 수는 (segment 수 × channel 수)로 작으므로
 * streaming/임계 처리가 필요 없다 — 메모리에서 바로 직렬화한다 (펀널 CSV와 동일 패턴).
 */
@Service
@RequiredArgsConstructor
public class SegmentChannelMatrixCsvExportService {

    private static final Logger log = LoggerFactory.getLogger(SegmentChannelMatrixCsvExportService.class);

    /** export 자체를 audit하는 action 코드. */
    static final String EXPORT_ACTION = "SEGMENT_CHANNEL_MATRIX_EXPORT";

    /** audit detail의 targetType — 다른 reports export와 일관되게 "SegmentChannelMatrix". */
    static final String EXPORT_TARGET_TYPE = "SegmentChannelMatrix";

    private final SegmentChannelMatrixService matrixService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 매트릭스 응답을 CSV로 응답 Writer에 출력한다.
     *
     * @return 출력한 row 수 (헤더 제외)
     */
    @Transactional
    public long exportToCsv(
            SegmentChannelMatrixQuery query,
            Writer out,
            Long actorMemberId,
            String ipAddress,
            String userAgent
    ) {
        // 검증·집계는 기존 서비스에 위임 (range 검증, BusinessException 동일).
        SegmentChannelMatrixResponse response = matrixService.getMatrix(query);

        try {
            out.write(AuditLogCsvExportService.UTF8_BOM);

            CsvWriter csv = new CsvWriter(out);
            csv.writeHeader(SegmentChannelMatrixCsvRow.HEADERS);

            long written = 0;
            for (SegmentChannelMatrixRow row : response.rows()) {
                csv.writeRow(SegmentChannelMatrixCsvRow.toRow(row));
                written++;
            }
            csv.flush();

            publishExportAudit(actorMemberId, query, ipAddress, userAgent, written);
            return written;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write segment-channel matrix CSV", e);
        }
    }

    /**
     * 본 export 자체를 audit_logs에 기록한다. {@link AuditRecordedEvent}는 AFTER_COMMIT에
     * 처리되므로 호출자 메서드가 트랜잭션 안에 있어야 listener가 동작한다 ({@link Transactional}).
     */
    private void publishExportAudit(
            Long actorMemberId, SegmentChannelMatrixQuery query,
            String ipAddress, String userAgent, long exportedCount
    ) {
        if (actorMemberId == null) {
            // 시스템/익명 호출은 audit 대상 외 (운영상 발생할 일이 없지만 방어).
            log.warn("segment-channel matrix export without actor — skipped self-audit");
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

    private static Map<String, Object> filterDetail(SegmentChannelMatrixQuery q) {
        Map<String, Object> m = new HashMap<>();
        if (q.projectId() != null) m.put("projectId", q.projectId());
        if (q.from() != null) m.put("from", q.from().toString());
        if (q.to() != null) m.put("to", q.to().toString());
        Set<Channel> channels = q.channels();
        if (channels != null && !channels.isEmpty()) {
            m.put("channels", channels.stream().map(Channel::name).collect(Collectors.toList()));
        }
        Set<Long> segmentIds = q.segmentIds();
        if (segmentIds != null && !segmentIds.isEmpty()) {
            // 정렬되지 않아도 audit 진단용으로 충분하지만, 가독성을 위해 안정 순서로 출력.
            m.put("segmentIds", segmentIds.stream().sorted().collect(Collectors.toList()));
        }
        if (q.sortBy() != null) m.put("sortBy", q.sortBy().name());
        m.put("ascending", q.ascending());
        return m;
    }
}
