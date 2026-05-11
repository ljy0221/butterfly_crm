package com.b101.butterfly.campaign.lifecycle.domain;

import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * {@link Campaign} 동적 검색용 Specification.
 *
 * <p>모든 필터는 nullable 이며 null 은 무시된다. butterfly-reporting 의
 * {@code SendReconciliationSpecifications} 패턴을 따른다.
 *
 * <p>인덱스 활용: {@code idx_campaigns_project} (projectId 우선 필터링).
 *
 * <p>⚠️ {@code q} 는 campaignName / campaignDescription LIKE 검색이라 앞 wildcard 가
 * 붙어 인덱스를 못 탄다. 캠페인 row 가 증가하면 풀스캔 위험 — FULLTEXT / 검색엔진 등으로
 * 개선 검토 필요 (별도 트랙, 미결 백로그 참조).
 */
public final class CampaignSpecifications {

    private CampaignSpecifications() {
    }

    public static Specification<Campaign> withFilters(
            Long projectId,
            CampaignStatus status,
            Channel channel,
            String q
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("campaignStatus"), status));
            }
            if (channel != null) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("campaignName"), pattern),
                        cb.like(root.get("campaignDescription"), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
