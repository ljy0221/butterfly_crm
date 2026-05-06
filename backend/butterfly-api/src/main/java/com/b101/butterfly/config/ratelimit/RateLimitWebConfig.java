package com.b101.butterfly.config.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rate limit 인터셉터 등록.
 *
 * <p>{@code /api/**} 경로에 적용하되 인증/콜백/sdk/health/swagger 등은 제외:
 * <ul>
 *   <li>{@code /api/v1/auth/**} — 로그인/회원가입은 별도 throttle 정책 (현재는 제외)</li>
 *   <li>{@code /api/notifications/callback/**} — 외부 채널 콜백</li>
 *   <li>{@code /sdk/**} — SDK 이벤트 수집 (apiKey 기반 별도 검증)</li>
 *   <li>{@code /actuator/**} — 헬스체크 등 인프라</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@RequiredArgsConstructor
public class RateLimitWebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/**",
                        "/api/notifications/callback/**",
                        "/sdk/**",
                        "/actuator/**"
                );
    }
}
