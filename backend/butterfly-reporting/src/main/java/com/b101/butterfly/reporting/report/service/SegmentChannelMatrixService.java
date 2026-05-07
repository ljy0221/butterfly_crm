package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.CampaignChannelStatsRow;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixQuery;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixResponse;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixRow;
import com.b101.butterfly.reporting.report.port.CampaignSegmentInfo;
import com.b101.butterfly.reporting.report.port.CampaignSegmentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Segment × Channel 성과 매트릭스 서비스 (S14P31B101-92).
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link CampaignStatsDailyRepository#aggregateByProjectAndDateRange}로 기간 내
 *       (campaignId, channel) 단위 합산을 받는다 (집계 테이블 우선).</li>
 *   <li>distinct campaignId 집합을 {@link CampaignSegmentPort}로 segment 매핑.</li>
 *   <li>(segmentId, channel) 단위로 재합산하고 비율 지표 계산 (분모 0 ⇒ null).</li>
 *   <li>필터·정렬 적용 후 highlights 추출.</li>
 * </ol>
 *
 * <p>ADR-0014 모듈 경계: campaign/segment 도메인을 직접 import하지 않고 SPI 포트로만 접근.
 */
@Service
@RequiredArgsConstructor
public class SegmentChannelMatrixService {

    /** KPI 정의서 §3 — 비율 소수점 자리수 통일. */
    private static final int RATE_SCALE = 2;
    /** 운영 보호 — 1년치(약 366일) 초과 조회는 거부. */
    private static final long MAX_RANGE_DAYS = 366;

    private final CampaignStatsDailyRepository campaignStatsDailyRepository;
    private final CampaignSegmentPort campaignSegmentPort;

    @Transactional(readOnly = true)
    public SegmentChannelMatrixResponse getMatrix(SegmentChannelMatrixQuery query) {
        validate(query);

        List<CampaignChannelStatsRow> rawRows = campaignStatsDailyRepository
                .aggregateByProjectAndDateRange(query.projectId(), query.from(), query.to());

        if (rawRows.isEmpty()) {
            return new SegmentChannelMatrixResponse(
                    query.projectId(), query.from(), query.to(),
                    List.of(),
                    new SegmentChannelMatrixResponse.Highlights(null, null, null, null)
            );
        }

        List<Long> campaignIds = rawRows.stream()
                .map(CampaignChannelStatsRow::campaignId)
                .distinct()
                .toList();
        Map<Long, CampaignSegmentInfo> segmentMap =
                campaignSegmentPort.findByCampaignIds(campaignIds);

        // (segmentId, channel) 단위로 재합산 — segmentId는 nullable (미지정 캠페인은 null로 그룹).
        Map<GroupKey, Aggregate> grouped = rawRows.stream().collect(
                Collectors.groupingBy(
                        row -> {
                            CampaignSegmentInfo info = segmentMap.get(row.campaignId());
                            Long segId = info == null ? null : info.segmentId();
                            String segName = info == null ? null : info.segmentName();
                            return new GroupKey(segId, segName, row.channel());
                        },
                        Collectors.reducing(Aggregate.zero(), Aggregate::from, Aggregate::add)
                )
        );

        List<SegmentChannelMatrixRow> allRows = grouped.entrySet().stream()
                .map(e -> toMatrixRow(e.getKey(), e.getValue()))
                .toList();

        List<SegmentChannelMatrixRow> filtered = applyFilters(allRows, query);
        List<SegmentChannelMatrixRow> sorted = applySort(filtered, query);

        return new SegmentChannelMatrixResponse(
                query.projectId(), query.from(), query.to(),
                sorted,
                buildHighlights(filtered)
        );
    }

    private void validate(SegmentChannelMatrixQuery query) {
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

    private static SegmentChannelMatrixRow toMatrixRow(GroupKey key, Aggregate agg) {
        return new SegmentChannelMatrixRow(
                key.segmentId(),
                key.segmentName(),
                key.channel(),
                agg.sentCount,
                agg.deliveredCount,
                agg.failedCount,
                agg.openCount,
                agg.clickCount,
                agg.convertCount,
                rate(agg.deliveredCount, agg.sentCount),
                rate(agg.openCount, agg.deliveredCount),
                rate(agg.clickCount, agg.openCount),
                rate(agg.convertCount, agg.sentCount)
        );
    }

    /** KPI 정의서 §3 — 분모 0이면 비율 정의 불가, null로 둔다. */
    private static BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static List<SegmentChannelMatrixRow> applyFilters(
            List<SegmentChannelMatrixRow> rows, SegmentChannelMatrixQuery query) {
        Set<Channel> channels = query.channels();
        Set<Long> segmentIds = query.segmentIds();
        return rows.stream()
                .filter(r -> channels == null || channels.isEmpty() || channels.contains(r.channel()))
                .filter(r -> segmentIds == null || segmentIds.isEmpty()
                        || (r.segmentId() != null && segmentIds.contains(r.segmentId())))
                .toList();
    }

    private static List<SegmentChannelMatrixRow> applySort(
            List<SegmentChannelMatrixRow> rows, SegmentChannelMatrixQuery query) {
        SegmentChannelMatrixQuery.SortBy sortBy =
                query.sortBy() == null ? SegmentChannelMatrixQuery.SortBy.SENT_COUNT : query.sortBy();
        Comparator<SegmentChannelMatrixRow> comparator = comparatorFor(sortBy);
        if (!query.ascending()) {
            comparator = comparator.reversed();
        }
        // 안정 정렬 — 동률은 segmentId(null 마지막) → channel 순으로 결정.
        comparator = comparator
                .thenComparing(SegmentChannelMatrixRow::segmentId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SegmentChannelMatrixRow::channel);
        return rows.stream().sorted(comparator).toList();
    }

    private static Comparator<SegmentChannelMatrixRow> comparatorFor(
            SegmentChannelMatrixQuery.SortBy sortBy) {
        return switch (sortBy) {
            case SENT_COUNT -> Comparator.comparingLong(SegmentChannelMatrixRow::sentCount);
            case DELIVERED_COUNT -> Comparator.comparingLong(SegmentChannelMatrixRow::deliveredCount);
            case OPEN_RATE -> Comparator.comparing(SegmentChannelMatrixRow::openRate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case CLICK_RATE -> Comparator.comparing(SegmentChannelMatrixRow::clickRate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case CONVERT_RATE -> Comparator.comparing(SegmentChannelMatrixRow::convertRate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private static SegmentChannelMatrixResponse.Highlights buildHighlights(
            List<SegmentChannelMatrixRow> rows) {
        return new SegmentChannelMatrixResponse.Highlights(
                topBy(rows, SegmentChannelMatrixRow::openRate, true),
                topBy(rows, SegmentChannelMatrixRow::clickRate, true),
                topBy(rows, SegmentChannelMatrixRow::convertRate, true),
                topBy(rows, SegmentChannelMatrixRow::openRate, false)
        );
    }

    /**
     * 비율이 null인 행은 highlight 대상에서 제외 (데이터 부족 행을 "1위/꼴등"으로 보여주면 오해 소지).
     */
    private static SegmentChannelMatrixRow topBy(
            List<SegmentChannelMatrixRow> rows,
            java.util.function.Function<SegmentChannelMatrixRow, BigDecimal> extractor,
            boolean descending) {
        var stream = rows.stream().filter(r -> extractor.apply(r) != null);
        Comparator<SegmentChannelMatrixRow> cmp = Comparator.comparing(extractor);
        return (descending ? stream.max(cmp) : stream.min(cmp)).orElse(null);
    }

    /** 그룹 키 — segmentId가 null이면 미지정 캠페인 그룹. */
    private record GroupKey(Long segmentId, String segmentName, Channel channel) {
    }

    /**
     * 그룹별 누적 카운트 — Stream.collect의 reducing identity로 사용.
     * record가 아닌 final class인 이유: identity로 같은 인스턴스가 재사용되지 않도록
     * {@link #add}가 새 인스턴스를 반환하는 불변 누적 패턴.
     */
    private record Aggregate(
            long sentCount, long deliveredCount, long failedCount,
            long openCount, long clickCount, long convertCount) {

        static Aggregate zero() {
            return new Aggregate(0, 0, 0, 0, 0, 0);
        }

        static Aggregate from(CampaignChannelStatsRow row) {
            return new Aggregate(
                    row.sentCount(), row.deliveredCount(), row.failedCount(),
                    row.openCount(), row.clickCount(), row.convertCount()
            );
        }

        Aggregate add(Aggregate other) {
            return new Aggregate(
                    sentCount + other.sentCount,
                    deliveredCount + other.deliveredCount,
                    failedCount + other.failedCount,
                    openCount + other.openCount,
                    clickCount + other.clickCount,
                    convertCount + other.convertCount
            );
        }
    }
}
