package com.b101.butterfly.reporting.report.dto;

/**
 * 캠페인 단계 펀널의 단일 row 합산 결과 (S14P31B101-90).
 *
 * <p>JPQL이 SUM 쿼리 결과를 본 record로 직접 매핑한다.
 */
public record FunnelCountRow(
        long sentCount,
        long deliveredCount,
        long failedCount,
        long openCount,
        long clickCount,
        long convertCount
) {

    public static FunnelCountRow zero() {
        return new FunnelCountRow(0, 0, 0, 0, 0, 0);
    }
}
