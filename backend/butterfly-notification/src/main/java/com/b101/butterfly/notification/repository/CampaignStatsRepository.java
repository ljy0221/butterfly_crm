package com.b101.butterfly.notification.repository;

import com.b101.butterfly.notification.entity.CampaignStats;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignStatsRepository extends JpaRepository<CampaignStats, Long> {
}
