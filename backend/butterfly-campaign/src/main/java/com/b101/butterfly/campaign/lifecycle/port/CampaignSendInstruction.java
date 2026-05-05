package com.b101.butterfly.campaign.campaign.domain.port;

import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import java.time.Instant;

/**
 * 발송 워커에게 전달되는 가벼운 지시서(Reference-only payload).
 *
 * <p>worker가 segmentId → segment_members 조회로 fan-out 책임. 메시지 내용/수신자 목록은 포함하지 않는다.
 * "발송 직전 최신 데이터" 원칙을 worker가 보장.
 *
 * <p>토픽: {@code campaign.send.requested}, partition key: projectId.
 */
public record CampaignSendInstruction(
        int schemaVersion,
        Long executionId,
        Long campaignId,
        Long projectId,
        Long templateId,
        Long segmentId,
        Channel channel,
        String idempotencyKey,
        Instant publishedAt
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static CampaignSendInstruction of(
            Long executionId,
            Long campaignId,
            Long projectId,
            Long templateId,
            Long segmentId,
            Channel channel,
            Instant publishedAt
    ) {
        return new CampaignSendInstruction(
                CURRENT_SCHEMA_VERSION,
                executionId,
                campaignId,
                projectId,
                templateId,
                segmentId,
                channel,
                "exec-" + executionId,
                publishedAt
        );
    }
}