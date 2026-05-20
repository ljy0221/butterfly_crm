package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository.CampaignFunnelRawMetricRow;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelQuery;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelResponse;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelStageRow;
import com.b101.butterfly.reporting.report.dto.FunnelCountRow;
import com.b101.butterfly.reporting.report.dto.FunnelStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 캠페인 단계 펀널 서비스 (S14P31B101-90).
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link CampaignStatsDailyRepository#aggregateFunnel}로 5단계 누적 카운트를 한 번에 조회.</li>
 *   <li>단계간 통과율(직전 대비)·시작 단계 대비 도달률·이탈률 계산.</li>
 *   <li>가장 큰 이탈 단계를 찾아 응답에 포함.</li>
 * </ol>
 *
 * <p><b>스코프</b>: 본 PR은 자동화 플로우 노드별 펀널이 아니라 캠페인 발송 lifecycle
 * (SENT → DELIVERED → OPEN → CLICK → CONVERT) 펀널에 한정한다 — 자동화 플로우 도메인
 * (S14P31B101-30)이 머지된 후 후속 티켓에서 노드 단위 펀널을 추가한다.
 */
@Service
@RequiredArgsConstructor
public class CampaignFunnelService {

    private static final int RATE_SCALE = 2;
    private static final long MAX_RANGE_DAYS = 366;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CampaignStatsDailyRepository campaignStatsDailyRepository;
    private final ReportingNotificationLogRepository notificationLogRepository;

    @Transactional(readOnly = true)
    public CampaignFunnelResponse getFunnel(CampaignFunnelQuery query) {
        validate(query);

        LocalDate today = LocalDate.now(SERVICE_ZONE);
        LocalDate dailyTo = query.to().isBefore(today) ? query.to() : today.minusDays(1);
        FunnelCountRow row = FunnelCountRow.zero();
        if (!query.from().isAfter(dailyTo)) {
            row = campaignStatsDailyRepository.aggregateFunnel(
                    query.projectId(),
                    query.from(),
                    dailyTo,
                    query.campaignId(),
                    query.channel()
            );
            if (row == null) {
                row = FunnelCountRow.zero();
            }
        }
        if (!query.from().isAfter(today) && !query.to().isBefore(today)) {
            row = plus(row, rawFunnelForDate(query, today));
        }

        List<CampaignFunnelStageRow> stages = buildStages(row);
        FunnelStage biggestDropOff = findBiggestDropOff(stages);

        return new CampaignFunnelResponse(
                query.projectId(),
                query.campaignId(),
                query.channel(),
                query.from(),
                query.to(),
                stages,
                biggestDropOff
        );
    }

    private void validate(CampaignFunnelQuery query) {
        if (query.from() == null || query.to() == null) {
            throw new BusinessException(AnalyticsErrorCode.INVALID_DATE_RANGE);
        }
        if (query.from().isAfter(query.to())) {
            throw new BusinessException(AnalyticsErrorCode.INVALID_DATE_RANGE);
        }
        long days = ChronoUnit.DAYS.between(query.from(), query.to()) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new BusinessException(AnalyticsErrorCode.DATE_RANGE_TOO_LARGE);
        }
    }

    private FunnelCountRow rawFunnelForDate(CampaignFunnelQuery query, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        String channelFilter = query.channel() == null ? null : query.channel().name();

        List<CampaignFunnelRawMetricRow> rows = notificationLogRepository.aggregateCampaignFunnelBetween(
                query.projectId(),
                from,
                to,
                channelFilter
        );

        long sent = 0L;
        long delivered = 0L;
        long failed = 0L;
        long open = 0L;
        long click = 0L;
        long convert = 0L;

        for (CampaignFunnelRawMetricRow row : rows) {
            if (query.campaignId() != null && !query.campaignId().equals(row.getCampaignId())) {
                continue;
            }
            sent += value(row.getSentCount());
            delivered += value(row.getDeliveredCount());
            failed += value(row.getFailedCount());
            open += value(row.getOpenCount());
            click += value(row.getClickCount());
            convert += value(row.getConvertCount());
        }
        return new FunnelCountRow(sent, delivered, failed, open, click, convert);
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static FunnelCountRow plus(FunnelCountRow left, FunnelCountRow right) {
        return new FunnelCountRow(
                left.sentCount() + right.sentCount(),
                left.deliveredCount() + right.deliveredCount(),
                left.failedCount() + right.failedCount(),
                left.openCount() + right.openCount(),
                left.clickCount() + right.clickCount(),
                left.convertCount() + right.convertCount()
        );
    }

    private static List<CampaignFunnelStageRow> buildStages(FunnelCountRow row) {
        long sent = row.sentCount();
        long delivered = row.deliveredCount();
        long open = row.openCount();
        long click = row.clickCount();
        long convert = row.convertCount();

        List<CampaignFunnelStageRow> stages = new ArrayList<>(5);
        // 첫 단계: 자기 자신 대비 100% (데이터 있을 때), 분모이자 기준점.
        stages.add(stage(FunnelStage.SENT, sent, sent, sent));
        stages.add(stage(FunnelStage.DELIVERED, delivered, sent, sent));
        stages.add(stage(FunnelStage.OPEN, open, sent, delivered));
        stages.add(stage(FunnelStage.CLICK, click, sent, open));
        stages.add(stage(FunnelStage.CONVERT, convert, sent, click));
        return stages;
    }

    private static CampaignFunnelStageRow stage(FunnelStage stage, long count, long start, long prev) {
        BigDecimal fromStart = ratePercent(count, start);
        BigDecimal fromPrev = ratePercent(count, prev);
        BigDecimal dropOff = fromPrev == null ? null : HUNDRED.subtract(fromPrev);
        return new CampaignFunnelStageRow(stage, count, fromStart, fromPrev, dropOff);
    }

    /** 분모 0이면 null (KPI 정의서 §3). */
    private static BigDecimal ratePercent(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 직전 단계 대비 이탈률(dropOffFromPrev)이 가장 큰 단계를 찾는다. 첫 단계(SENT)는
     * 직전 단계가 자기 자신이라 항상 0이므로 후보에서 제외. dropOff null인 단계도 제외.
     */
    private static FunnelStage findBiggestDropOff(List<CampaignFunnelStageRow> stages) {
        FunnelStage best = null;
        BigDecimal bestRate = null;
        for (int i = 1; i < stages.size(); i++) {
            CampaignFunnelStageRow s = stages.get(i);
            BigDecimal d = s.dropOffFromPrev();
            if (d == null) {
                continue;
            }
            if (bestRate == null || d.compareTo(bestRate) > 0) {
                bestRate = d;
                best = s.stage();
            }
        }
        return best;
    }
}
