package com.b101.butterfly.campaign.lifecycle.port;

import java.time.Instant;

/**
 * 캠페인 회차 발송 결과 통보 페이로드 — notification 이 publish, worker 가 consume.
 *
 * <p>{@link CampaignSendInstruction} 의 역방향(결과 통보) 한 쌍. 한 회차 발송이 끝나면
 * notification 측이 per-execution summary 1건을 publish 하고, worker 측 consumer 가 받아
 * {@code CampaignExecutionResultRecorder} 진입점을 호출해 {@code SEND → SUCCESS|FAIL} 전이를 수행한다.
 *
 * <p>SUCCESS/FAIL 판정은 consumer 측 단일 지점 — {@code sentCount >= 1} 면 SUCCESS, else FAIL
 * (S14P31B101-284 도메인 사양).
 *
 * <p>토픽: {@code campaign-execution-result}, partition key: projectId.
 */
public record CampaignExecutionResult(
        int schemaVersion,
        Long executionId,
        Long campaignId,
        Long projectId,
        int sentCount,
        int failedCount,
        Instant completedAt
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static CampaignExecutionResult of(
            Long executionId,
            Long campaignId,
            Long projectId,
            int sentCount,
            int failedCount,
            Instant completedAt
    ) {
        return new CampaignExecutionResult(
                CURRENT_SCHEMA_VERSION,
                executionId,
                campaignId,
                projectId,
                sentCount,
                failedCount,
                completedAt
        );
    }
}
