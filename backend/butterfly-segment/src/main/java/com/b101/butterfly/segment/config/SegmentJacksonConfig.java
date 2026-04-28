package com.b101.butterfly.segment.config;

import com.b101.butterfly.segment.domain.condition.json.ConditionTreeJsonModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring MVC가 사용하는 ObjectMapper에 ConditionTreeJsonModule을 자동 등록한다.
 * Spring Boot는 컨텍스트 내 모든 Jackson Module 빈을 ObjectMapper에 등록한다.
 */
@Configuration
public class SegmentJacksonConfig {

    @Bean
    public ConditionTreeJsonModule conditionTreeJsonModule() {
        return new ConditionTreeJsonModule();
    }
}
