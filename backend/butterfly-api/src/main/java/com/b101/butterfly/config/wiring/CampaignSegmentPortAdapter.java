package com.b101.butterfly.config.wiring;

import com.b101.butterfly.reporting.report.port.CampaignSegmentInfo;
import com.b101.butterfly.reporting.report.port.CampaignSegmentPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * butterfly-api 조립 레벨에서 reporting 모듈의 SPI({@link CampaignSegmentPort})를
 * campaign 모듈의 {@code Campaign} + segment 모듈의 {@code Segment} JPQL JOIN으로 충족시키는
 * 어댑터 (S14P31B101-92).
 *
 * <p>본 클래스를 campaign 모듈에 두지 않고 api 모듈에 둔 이유: 다른 wiring 어댑터(예:
 * {@link CampaignExecutionPortAdapter})와 동일 — campaign → reporting 의존을 만들지 않고,
 * 본 어댑터는 비즈니스 로직 없는 "조립"의 일부.
 *
 * <p>세그먼트가 LEFT JOIN인 이유: 캠페인 중 {@code segment_id IS NULL}인 "전체 발송"이
 * 존재한다 (V1__core_baseline.sql의 campaigns.segment_id NULL 허용). 이런 경우 매트릭스에서
 * "세그먼트 미지정" 행으로 노출되어야 하므로 INNER JOIN으로 누락하면 안 된다.
 */
@Component
public class CampaignSegmentPortAdapter implements CampaignSegmentPort {

    private static final String FIND_BY_CAMPAIGN_IDS_JPQL = """
            SELECT c.campaignId, s.id, s.name
            FROM com.b101.butterfly.campaign.lifecycle.domain.Campaign c
            LEFT JOIN com.b101.butterfly.segment.entity.Segment s
                ON s.id = c.segmentId
            WHERE c.campaignId IN :campaignIds
            """;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CampaignSegmentInfo> findByCampaignIds(List<Long> campaignIds) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createQuery(FIND_BY_CAMPAIGN_IDS_JPQL)
                .setParameter("campaignIds", campaignIds)
                .getResultList();

        Map<Long, CampaignSegmentInfo> result = new HashMap<>(rows.size());
        for (Object[] row : rows) {
            Long campaignId = (Long) row[0];
            Long segmentId = (Long) row[1];
            String segmentName = (String) row[2];
            result.put(campaignId, new CampaignSegmentInfo(campaignId, segmentId, segmentName));
        }
        return result;
    }
}
