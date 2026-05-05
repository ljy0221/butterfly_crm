package com.b101.butterfly.campaign.lifecycle.domain;

import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import java.time.LocalDateTime;

/**
 * CampaignExecution + Campaign JOIN 결과를 담는 JPQL 프로젝션.
 *
 * <p>외부(reporting BC)로 노출하기 전 단계의 내부 표현. 어댑터({@code CampaignExecutionPortAdapter})
 * 가 reporting의 {@code CampaignExecutionInfo}로 변환하면서 Channel enum 도 함께 매핑한다.
 */
public record CampaignExecutionInfoProjection(
        Long executionId,
        Long campaignId,
        Long projectId,
        Channel channel,
        Integer targetCount,
        LocalDateTime executedAt
) {
}
