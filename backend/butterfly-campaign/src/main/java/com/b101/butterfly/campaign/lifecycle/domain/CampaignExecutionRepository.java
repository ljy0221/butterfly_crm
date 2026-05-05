package com.b101.butterfly.campaign.lifecycle.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignExecutionRepository extends JpaRepository<CampaignExecution, Long> {
}
