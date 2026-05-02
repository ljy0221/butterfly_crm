package com.b101.butterfly.campaign.campaign.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    
    List<Campaign> findAllByMessageTemplate_messageTemplateId(Long messageTemplateId);
}
