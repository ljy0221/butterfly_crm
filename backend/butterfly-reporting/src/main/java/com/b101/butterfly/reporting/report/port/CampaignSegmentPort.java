package com.b101.butterfly.reporting.report.port;

import java.util.List;
import java.util.Map;

/**
 * 캠페인 ↔ 세그먼트 매핑 조회 SPI (S14P31B101-92).
 *
 * <p>segment-channel 성과 매트릭스가 {@code campaign_stats_daily}의 캠페인 ID 집합을
 * 세그먼트 ID로 환원할 때 사용한다. reporting 모듈은 본 인터페이스를 통해서만 외부 데이터에
 * 접근하고, 실제 어댑터는 butterfly-api 조립 레벨에서 campaign 모듈의 도메인을 JPQL로 조회한다.
 *
 * <p>인터페이스 형태가 외부 합의 결과로 바뀌어도 도메인 코드는 영향받지 않는 격리 경계.
 */
public interface CampaignSegmentPort {

    /**
     * 주어진 캠페인 ID들의 세그먼트 매핑을 일괄 조회한다.
     *
     * <p>호출자는 campaign_stats_daily에서 얻은 distinct campaignId 집합을 그대로 넘긴다.
     * 결과는 캠페인 ID → 메타 매핑이며, 입력 ID 중 어댑터가 찾지 못한 것은 결과 맵에서 빠진다.
     *
     * <p>{@code campaignIds}가 비어 있으면 빈 맵을 반환한다 — IN () 빈 절 방지.
     */
    Map<Long, CampaignSegmentInfo> findByCampaignIds(List<Long> campaignIds);
}
