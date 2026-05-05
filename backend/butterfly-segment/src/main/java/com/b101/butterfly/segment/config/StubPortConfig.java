package com.b101.butterfly.segment.config;

import com.b101.butterfly.segment.port.EventAggReader;
import com.b101.butterfly.segment.port.LinkedCampaignChecker;
import com.b101.butterfly.segment.port.UserDirectoryReader;
import com.b101.butterfly.segment.port.UserSummaryReader;
import com.b101.butterfly.segment.port.stub.StubEventAggReader;
import com.b101.butterfly.segment.port.stub.StubLinkedCampaignChecker;
import com.b101.butterfly.segment.port.stub.StubUserDirectoryReader;
import com.b101.butterfly.segment.port.stub.StubUserSummaryReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 외부 모듈(customer / campaign) 미합류 시 stub 빈을 자동 등록한다.
 *
 * <p>{@code @Component} + {@code @ConditionalOnMissingBean}을 클래스 레벨에 직접 붙이면 component
 * scan 시점과 ConditionalOnMissingBean 평가 타이밍이 어긋나 자기 자신을 발견하고 등록을 거부하는 케이스가
 * 발생한다 (Spring Boot 3.x). 표준 패턴인 {@code @Configuration + @Bean + @ConditionalOnMissingBean}
 * (반환 타입 = 포트 인터페이스)으로 전환해 customer / campaign 모듈이 진짜 빈을 등록하면 본 stub이
 * 자동으로 back-off 되도록 한다.
 */
@Configuration
public class StubPortConfig {

    @Bean
    @ConditionalOnMissingBean(LinkedCampaignChecker.class)
    public LinkedCampaignChecker stubLinkedCampaignChecker() {
        return new StubLinkedCampaignChecker();
    }

    @Bean
    @ConditionalOnMissingBean(UserDirectoryReader.class)
    public UserDirectoryReader stubUserDirectoryReader() {
        return new StubUserDirectoryReader();
    }

    @Bean
    @ConditionalOnMissingBean(EventAggReader.class)
    public EventAggReader stubEventAggReader() {
        return new StubEventAggReader();
    }

    @Bean
    @ConditionalOnMissingBean(UserSummaryReader.class)
    public UserSummaryReader stubUserSummaryReader() {
        return new StubUserSummaryReader();
    }
}
