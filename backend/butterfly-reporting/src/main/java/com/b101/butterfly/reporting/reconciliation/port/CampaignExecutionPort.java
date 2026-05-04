package com.b101.butterfly.reporting.reconciliation.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 정합성 검증 도메인이 외부 팀(`campaign_executions`)의 데이터를 조회하기 위한 SPI.
 *
 * <p>도메인은 본 인터페이스를 통해서만 외부 데이터에 접근하고, 실제 어댑터(REST·Spring bean·DB
 * 직접 조회)는 S14P31B101-187 (126-B) 에서 외부 팀 합의 후 구현한다. 본 PR(126-A)에서는
 * 인터페이스 정의 + 테스트용 in-memory stub만 제공한다.
 *
 * <p>인터페이스 형태가 외부 합의 결과로 바뀌어도 도메인 코드는 영향받지 않는 격리 경계.
 */
public interface CampaignExecutionPort {

    /**
     * 단일 회차 메타 조회. 미존재 시 {@link Optional#empty()}.
     */
    Optional<CampaignExecutionInfo> find(Long executionId);

    /**
     * 캠페인의 모든 회차 메타 조회.
     */
    List<CampaignExecutionInfo> findByCampaign(Long campaignId);

    /**
     * 자동 배치(126-B)가 직전 24시간 회차를 검증할 때 쓴다 — `executedAt >= since` 인 회차들.
     */
    List<CampaignExecutionInfo> findRecent(LocalDateTime since);
}
