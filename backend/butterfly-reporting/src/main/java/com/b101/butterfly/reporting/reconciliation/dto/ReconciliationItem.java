package com.b101.butterfly.reporting.reconciliation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.reconciliation.entity.SendReconciliation;

import java.time.LocalDateTime;

/**
 * 정합성 검증 결과 응답 항목 (API #24, #25).
 */
public record ReconciliationItem(
        Long reconId,
        Long projectId,
        Long campaignId,
        Long campaignExecutionId,
        Channel channel,
        int expectedCount,
        int actualCount,
        int sentCount,
        int failedCount,
        int diffCount,
        boolean consistent,
        String note,
        LocalDateTime checkedAt
) {
    public static ReconciliationItem from(SendReconciliation entity) {
        return new ReconciliationItem(
                entity.getId(),
                entity.getProjectId(),
                entity.getCampaignId(),
                entity.getCampaignExecutionId(),
                entity.getChannel(),
                entity.getExpectedCount(),
                entity.getActualCount(),
                entity.getSentCount(),
                entity.getFailedCount(),
                entity.getDiffCount(),
                entity.isConsistent(),
                entity.getNote(),
                entity.getCheckedAt()
        );
    }
}
