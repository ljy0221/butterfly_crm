package com.b101.butterfly.config.wiring;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionInfo;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * butterfly-api 조립 레벨에서 reporting 모듈의 SPI({@link CampaignExecutionPort})를
 * lifecycle BC의 {@code CampaignExecution} + {@code Campaign} JPQL JOIN으로 충족시키는 어댑터.
 *
 * <p>본 클래스를 campaign 모듈에 두지 않고 api 모듈에 둔 이유:
 * <ul>
 *   <li>campaign → reporting 모듈 의존을 만들지 않기 위함 (BC 격리)</li>
 *   <li>본 어댑터는 "조립(wiring)"의 일부 — 비즈니스 로직 없음, 프로젝션 + 변환만</li>
 * </ul>
 *
 * <p>채널 enum은 도메인이 다르므로 동일 이름 매칭으로 변환 — campaign messagetemplate Channel
 * → reporting notification Channel.
 */
@Component
public class CampaignExecutionPortAdapter implements CampaignExecutionPort {

    private static final String FIND_BY_ID_JPQL = """
            SELECT e.campaignExecutionId, e.campaignId, c.projectId, c.channel,
                   e.targetCount, COALESCE(e.sendStartedAt, e.createdAt)
            FROM com.b101.butterfly.campaign.lifecycle.domain.CampaignExecution e
            JOIN com.b101.butterfly.campaign.lifecycle.domain.Campaign c
                ON c.campaignId = e.campaignId
            WHERE e.campaignExecutionId = :executionId
            """;

    private static final String FIND_BY_CAMPAIGN_JPQL = """
            SELECT e.campaignExecutionId, e.campaignId, c.projectId, c.channel,
                   e.targetCount, COALESCE(e.sendStartedAt, e.createdAt)
            FROM com.b101.butterfly.campaign.lifecycle.domain.CampaignExecution e
            JOIN com.b101.butterfly.campaign.lifecycle.domain.Campaign c
                ON c.campaignId = e.campaignId
            WHERE e.campaignId = :campaignId
            """;

    private static final String FIND_RECENT_JPQL = """
            SELECT e.campaignExecutionId, e.campaignId, c.projectId, c.channel,
                   e.targetCount, COALESCE(e.sendStartedAt, e.createdAt)
            FROM com.b101.butterfly.campaign.lifecycle.domain.CampaignExecution e
            JOIN com.b101.butterfly.campaign.lifecycle.domain.Campaign c
                ON c.campaignId = e.campaignId
            WHERE COALESCE(e.sendStartedAt, e.createdAt) >= :since
            """;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Optional<CampaignExecutionInfo> find(Long executionId) {
        try {
            Object[] row = (Object[]) em.createQuery(FIND_BY_ID_JPQL)
                    .setParameter("executionId", executionId)
                    .getSingleResult();
            return Optional.of(toInfo(row));
        } catch (jakarta.persistence.NoResultException | EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignExecutionInfo> findByCampaign(Long campaignId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createQuery(FIND_BY_CAMPAIGN_JPQL)
                .setParameter("campaignId", campaignId)
                .getResultList();
        return rows.stream().map(this::toInfo).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignExecutionInfo> findRecent(LocalDateTime since) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createQuery(FIND_RECENT_JPQL)
                .setParameter("since", since)
                .getResultList();
        return rows.stream().map(this::toInfo).toList();
    }

    private CampaignExecutionInfo toInfo(Object[] row) {
        Long executionId = (Long) row[0];
        Long campaignId = (Long) row[1];
        Long projectId = (Long) row[2];
        Enum<?> sourceChannel = (Enum<?>) row[3];
        Integer targetCount = (Integer) row[4];
        LocalDateTime executedAt = (LocalDateTime) row[5];
        return new CampaignExecutionInfo(
                executionId,
                campaignId,
                projectId,
                Channel.valueOf(sourceChannel.name()),
                targetCount == null ? 0 : targetCount,
                executedAt
        );
    }
}
