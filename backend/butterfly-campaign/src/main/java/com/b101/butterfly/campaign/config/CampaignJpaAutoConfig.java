package com.b101.butterfly.campaign.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@EntityScan("com.b101.butterfly.campaign")
@EnableJpaRepositories("com.b101.butterfly.campaign")
public class CampaignJpaAutoConfig {

}
