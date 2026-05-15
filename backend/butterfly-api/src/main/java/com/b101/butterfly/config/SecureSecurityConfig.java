package com.b101.butterfly.config;

import com.b101.butterfly.auth.service.JwtTokenProvider;
import com.b101.butterfly.auth.service.RedisService;
import com.b101.butterfly.config.security.Http401EntryPoint;
import com.b101.butterfly.config.security.Http403Handler;
import com.b101.butterfly.config.security.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * prod 프로파일 전용 Security 설정.
 * JWT 인증 필터 + @PreAuthorize 기반 역할 인가 활성화.
 * 로컬/개발 환경은 SecurityConfig(PermissiveSecurityConfig) 사용.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("prod")
@EnableConfigurationProperties(CorsProperties.class)
public class SecureSecurityConfig {

    @Bean
    public SecurityFilterChain secureFilterChain(HttpSecurity http,
            CorsConfigurationSource corsSource,
            JwtTokenProvider jwtTokenProvider,
            RedisService redisService) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, redisService),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // 공개 인증 경로
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/email/**",
                                "/api/v1/auth/owner/set-password",
                                "/api/v1/applications"
                        ).permitAll()
                        // SDK / 내부 경로 — 자체 토큰 방식으로 처리
                        .requestMatchers("/sdk/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        // SDK 표면 — ApiKeyAuthenticationFilter 가 X-API-Key 인증 처리 (ADR-0017)
                        .requestMatchers("/api/v1/events/**", "/api/v1/devices/**").permitAll()
                        // K8s readiness/liveness probe + Prometheus scrape (S14P31B101-281)
                        // 인증 거치면 readiness 401 → Pod NotReady → progress deadline 초과 → CrashLoop.
                        // 외부 노출은 받은 EC2 SG가 worker NodePort를 받은 EC2 IP/32로만 제한해 보호.
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        // 플랫폼 관리자 전용
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 나머지 전부 JWT 인증 필요
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new Http401EntryPoint())
                        .accessDeniedHandler(new Http403Handler())
                );

        return http.build();
    }

    @Bean
    @Primary
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.allowedOrigins());
        cfg.setAllowedMethods(props.allowedMethods());
        cfg.setAllowedHeaders(props.allowedHeaders());
        cfg.setExposedHeaders(props.exposedHeaders());
        cfg.setAllowCredentials(props.allowCredentials());
        cfg.setMaxAge(props.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
