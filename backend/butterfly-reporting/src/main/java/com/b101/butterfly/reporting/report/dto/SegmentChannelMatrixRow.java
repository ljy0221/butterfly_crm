package com.b101.butterfly.reporting.report.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

import java.math.BigDecimal;

/**
 * Segment × Channel 성과 매트릭스의 단일 행 (S14P31B101-92 응답).
 *
 * <p>{@code segmentId}/{@code segmentName}은 "세그먼트 미지정 캠페인" 그룹에서 모두 null이다
 * (전체 발송으로 매트릭스의 별도 행으로 표시).
 *
 * <p>비율 지표는 분모가 0이면 null — KPI 정의서 §3 (분모 0 ⇒ 비율 정의 불가).
 */
public record SegmentChannelMatrixRow(
        Long segmentId,
        String segmentName,
        Channel channel,
        long sentCount,
        long deliveredCount,
        long failedCount,
        long openCount,
        long clickCount,
        long convertCount,
        BigDecimal deliveryRate,
        BigDecimal openRate,
        BigDecimal clickRate,
        BigDecimal convertRate
) {
}
