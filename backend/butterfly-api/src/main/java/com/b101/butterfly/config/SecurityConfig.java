package com.b101.butterfly.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 임시 Security 설정. 인증 모듈 완성 시 교체할 것.
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
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers("/sdk/**").permitAll()
                        .anyRequest().permitAll()  // TODO: 인증 모듈 완성 후 authenticated()로 변경
                );

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
