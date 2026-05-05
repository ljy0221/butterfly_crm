package com.b101.butterfly.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 설정 — 환경변수 {@code BUTTERFLY_CORS_ALLOWED_ORIGINS} (또는 application.properties의
 * {@code butterfly.cors.allowed-origins}) 로 명시 origin을 주입한다.
 *
 * <p>보안 정책: {@code allowCredentials=true}일 때 wildcard origin은 RFC 6454 위반이므로
 * 운영에서는 반드시 명시 origin 사용. 로컬/개발 편의를 위해 기본값으로 일반적인 SPA 포트를 둔다.
 */
@ConfigurationProperties(prefix = "butterfly.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        Boolean allowCredentials,
        Long maxAgeSeconds
) {

    private static final List<String> DEFAULT_ORIGINS = List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:5174",
            "http://localhost:5175",
            "http://localhost:8080"
    );
    private static final List<String> DEFAULT_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> DEFAULT_HEADERS = List.of("*");
    private static final List<String> DEFAULT_EXPOSED = List.of("Idempotency-Key");
    private static final boolean DEFAULT_CREDENTIALS = true;
    private static final long DEFAULT_MAX_AGE = 3600L;

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = DEFAULT_ORIGINS;
        }
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            allowedMethods = DEFAULT_METHODS;
        }
        if (allowedHeaders == null || allowedHeaders.isEmpty()) {
            allowedHeaders = DEFAULT_HEADERS;
        }
        if (exposedHeaders == null) {
            exposedHeaders = DEFAULT_EXPOSED;
        }
        if (allowCredentials == null) {
            allowCredentials = DEFAULT_CREDENTIALS;
        }
        if (maxAgeSeconds == null) {
            maxAgeSeconds = DEFAULT_MAX_AGE;
        }
    }
}
