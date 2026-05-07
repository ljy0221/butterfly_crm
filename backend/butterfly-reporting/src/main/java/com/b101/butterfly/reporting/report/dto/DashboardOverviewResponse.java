package com.b101.butterfly.reporting.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 운영자 대시보드 종합 응답 (S14P31B101-33).
 *
 * <p>단일 호출로 진입 카드 전체를 채울 수 있도록 요약·실패 TOP N을 한 번에 반환한다
 * (인수 조건 §1 — 집계 테이블만 조회, 원장 직접 조회 금지).
 *
 * <p>{@link #updatedAt}은 응답에 사용된 {@code project_stats_daily} 행의 마지막
 * 갱신 시각 — 클라이언트가 "데이터 최신성"을 표시할 수 있게 한다. 데이터가 없으면 null.
 */
public record DashboardOverviewResponse(
        Long projectId,
        LocalDate bucketDate,
        Summary summary,
        List<FailureTopRow> failuresTop,
        LocalDateTime updatedAt
) {

    /**
     * 일자 요약 카드 — {@code project_stats_daily}의 한 row를 1:1로 노출.
     * 데이터가 없는 날은 모두 0/null로 채운 zero 인스턴스를 반환한다.
     */
    public record Summary(
            int activeUserCount,
            int newUserCount,
            int churnUserCount,
            long totalSent,
            long totalDelivered,
            long totalFailed,
            long totalOpen,
            long totalClick,
            long totalConvert,
            BigDecimal pushOpenRate,
            BigDecimal overallClickRate,
            BigDecimal overallConvertRate,
            int activeCampaignCount
    ) {

        public static Summary zero() {
            return new Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, 0);
        }
    }
}
