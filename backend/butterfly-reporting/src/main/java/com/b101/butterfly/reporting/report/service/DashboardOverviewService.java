package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.reporting.aggregation.entity.ProjectStatsDaily;
import com.b101.butterfly.reporting.aggregation.repository.FailureStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.ProjectStatsDailyRepository;
import com.b101.butterfly.reporting.report.dto.DashboardOverviewResponse;
import com.b101.butterfly.reporting.report.dto.DashboardOverviewResponse.Summary;
import com.b101.butterfly.reporting.report.dto.FailureCountRow;
import com.b101.butterfly.reporting.report.dto.FailureTopRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 운영자 대시보드 종합 서비스 (S14P31B101-33).
 *
 * <p>흐름:
 * <ol>
 *   <li>{@code project_stats_daily}에서 일자 요약 1 row 조회 — 데이터가 없으면 zero summary</li>
 *   <li>{@code failure_stats_daily}에서 같은 프로젝트·일자의 실패를 (channel, error_code)로
 *       합산하여 failed_count DESC 순으로 TOP N 추출</li>
 *   <li>총 실패 건수를 분모로 ratePercent 계산 (분모 0이면 null — KPI 정의서 §3)</li>
 * </ol>
 *
 * <p><b>스코프 (MVP)</b>: 명세 4개 endpoint 중 dashboard 요약 + failure top만 본 PR에서 다룬다.
 * 최근 캠페인 5건은 campaign 도메인 SPI 추가 필요로 별도 티켓, 시스템 헬스(Kafka lag 등)는
 * Micrometer 메트릭 인프라 의존으로 후속.
 *
 * <p><b>집계 테이블 우선 조회 정책</b>: ADR-0003 — 운영 대시보드는 {@code notification_logs} 같은
 * 원장을 직접 조회하지 않고 일/시간 단위 집계 테이블만 본다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardOverviewService {

    /** 본 PR의 default failure top N 개수 — Controller가 limit 파라미터로 override 가능. */
    public static final int DEFAULT_FAILURE_TOP_LIMIT = 5;
    private static final int RATE_SCALE = 2;

    private final ProjectStatsDailyRepository projectStatsDailyRepository;
    private final FailureStatsDailyRepository failureStatsDailyRepository;

    public DashboardOverviewResponse getOverview(Long projectId, LocalDate bucketDate, int failureLimit) {
        ProjectStatsDaily stats = projectStatsDailyRepository
                .findByProjectIdAndBucketDate(projectId, bucketDate)
                .orElse(null);

        Summary summary = stats == null ? Summary.zero() : toSummary(stats);

        List<FailureCountRow> rawFailures = failureStatsDailyRepository
                .findTopByProjectAndDate(projectId, bucketDate, PageRequest.of(0, failureLimit));

        long totalFailed = rawFailures.stream().mapToLong(FailureCountRow::failedCount).sum();
        List<FailureTopRow> failures = rawFailures.stream()
                .map(row -> toFailureTop(row, totalFailed))
                .toList();

        return new DashboardOverviewResponse(
                projectId,
                bucketDate,
                summary,
                failures,
                stats == null ? null : stats.getUpdatedAt()
        );
    }

    private static Summary toSummary(ProjectStatsDaily s) {
        return new Summary(
                s.getActiveUserCount(),
                s.getNewUserCount(),
                s.getChurnUserCount(),
                s.getTotalSent(),
                s.getTotalDelivered(),
                s.getTotalFailed(),
                s.getTotalOpen(),
                s.getTotalClick(),
                s.getTotalConvert(),
                s.getPushOpenRate(),
                s.getOverallClickRate(),
                s.getOverallConvertRate(),
                s.getActiveCampaignCount()
        );
    }

    private static FailureTopRow toFailureTop(FailureCountRow row, long totalFailed) {
        return new FailureTopRow(
                row.channel(),
                row.errorCode(),
                row.failedCount(),
                row.affectedCampaignCount(),
                row.affectedUserCount(),
                ratePercent(row.failedCount(), totalFailed)
        );
    }

    /** KPI 정의서 §3 — 분모 0이면 비율 정의 불가, null로 둔다. */
    private static BigDecimal ratePercent(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP);
    }
}
