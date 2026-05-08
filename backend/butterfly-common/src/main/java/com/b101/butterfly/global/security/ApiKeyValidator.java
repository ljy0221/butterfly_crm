package com.b101.butterfly.global.security;

import java.util.Optional;

/**
 * Public API Key 검증 SPI.
 *
 * <p>butterfly-tenant가 구현체를 제공하고, butterfly-api의 ApiKeyAuthenticationFilter가
 * 이 인터페이스를 통해 프로젝트 ID를 조회한다. 호출자는 SHA-256 해시된 키를 전달해야 한다.
 */
public interface ApiKeyValidator {

    /**
     * SHA-256 해시된 API 키로 소속 프로젝트 ID를 조회한다.
     *
     * @param sha256 SHA-256(plain API key) — 64자 hex 문자열
     * @return 유효한 키면 projectId, 없으면 empty
     */
    Optional<Long> resolveProjectId(String sha256);
}
