package com.b101.butterfly.reporting.report.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

import java.math.BigDecimal;

/**
 * 실패 원인 TOP N의 단일 행 (S14P31B101-33).
 *
 * <p>운영자 대시보드와 별도 endpoint(`GET /failures/top`)에서 공통으로 쓴다.
 *
 * <p>{@code ratePercent}는 본 프로젝트의 같은 일자 총 실패 건수 대비 비율 (%) — 분모 0이면
 * null (KPI 정의서 §3).
 */
public record FailureTopRow(
        Channel channel,
        String errorCode,
        long failedCount,
        int affectedCampaignCount,
        int affectedUserCount,
        BigDecimal ratePercent
) {
}
