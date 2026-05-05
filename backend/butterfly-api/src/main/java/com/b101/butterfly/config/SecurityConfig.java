package com.b101.butterfly.config;

import com.b101.butterfly.config.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * ADR-0005: Security 설정. JWT 인증 + 프로젝트별 역할 인가.
 *
 * <p>JWT 인증 흐름:
 * <ol>
 *   <li>{@link JwtAuthenticationFilter}가 Authorization 헤더 파싱 → SecurityContext에 principal 주입</li>
 *   <li>STATELESS 세션 — JWT만 신뢰</li>
 *   <li>{@code @EnableMethodSecurity}로 컨트롤러 {@code @PreAuthorize("@projectPermission.hasRole(...)")}
 *       활성화 (ProjectPermissionEvaluator는 auth 모듈 빈)</li>
 * </ol>
 *
 * <p>permitAll 경로:
 * <ul>
 *   <li>{@code /api/v1/auth/**} — 로그인/회원가입/이메일 인증/토큰 갱신</li>
 *   <li>{@code /api/v1/applications} — 신청서 (인증 전 단계)</li>
 *   <li>{@code /sdk/**} — SDK 이벤트 수집 (apiKey 기반 별도 검증)</li>
 *   <li>{@code /api/notifications/callback/**} — 외부 채널 콜백</li>
 *   <li>{@code /api/test/**}, {@code /actuator/health} — 진단</li>
 * </ul>
 *
 * <p>CORS: PATCH(상태 전이 sub-resource), POST/PUT/DELETE/GET 명시 허용.
 * 클라이언트(브라우저 SPA, 모바일 SDK)에서 PATCH method가 차단되지 않도록 한다.
 *
 * <p>보안: {@code allowCredentials=true}와 wildcard origin 조합은 RFC 6454 위반이며 prod 사용 금지.
 * Origin은 {@link CorsProperties}로 명시 주입한다 (env: {@code BUTTERFLY_CORS_ALLOWED_ORIGINS},
 * properties: {@code butterfly.cors.allowed-origins}). 기본값에 dev SPA 포트 (3000/5173-5175/8080) 포함.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/applications").permitAll()
                        .requestMatchers("/sdk/**").permitAll()
                        .requestMatchers("/api/notifications/callback/**").permitAll()
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        // 명시 origin 사용 — wildcard + allowCredentials 조합은 브라우저가 거부함.
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
