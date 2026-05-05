package com.b101.butterfly.campaign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @WebMvcTest 가 @SpringBootConfiguration 을 검색할 수 있도록 제공하는 테스트 전용 최소 부트 설정.
 *
 * <p>실제 애플리케이션 실행에 사용되지 않음.
 */
@SpringBootApplication
public class CampaignTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampaignTestApplication.class, args);
    }
}
