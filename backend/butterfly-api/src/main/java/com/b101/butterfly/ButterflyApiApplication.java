package com.b101.butterfly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.b101.butterfly")
@EnableAsync  // EmailService @Async 비동기 발송
// @EnableMethodSecurity — 인증 통합 시 활성화 (prod 프로파일 전환 후)
public class ButterflyApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ButterflyApiApplication.class, args);
	}
}
