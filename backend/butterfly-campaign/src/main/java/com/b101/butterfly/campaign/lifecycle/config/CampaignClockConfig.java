package com.b101.butterfly.campaign.lifecycle.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 캠페인 도메인의 시간 의존을 추상화하기 위한 {@link Clock} 빈.
 *
 * <p>Asia/Seoul 고정 (CLAUDE.md 타임존 정책). 테스트에서는 fixed Clock을 주입해 검증.
 */
@AutoConfiguration
public class CampaignClockConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock campaignClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
