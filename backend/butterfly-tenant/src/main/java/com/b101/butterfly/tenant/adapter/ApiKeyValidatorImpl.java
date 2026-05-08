package com.b101.butterfly.tenant.adapter;

import com.b101.butterfly.global.security.ApiKeyValidator;
import com.b101.butterfly.tenant.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * ApiKeyValidator SPI 구현체 (butterfly-tenant).
 *
 * <p>SHA-256 해시된 API 키로 projects 테이블을 조회해 projectId를 반환한다.
 * 호출 전 포맷 검증(pk_live_ 접두사, 32자)을 수행해 불필요한 DB 조회를 방지한다.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyValidatorImpl implements ApiKeyValidator {

    private static final String KEY_PREFIX = "pk_live_";
    private static final int KEY_LENGTH = 32;

    private final ProjectRepository projectRepository;

    @Override
    public Optional<Long> resolveProjectId(String sha256) {
        return projectRepository.findByPublicApiKey(sha256)
                .map(project -> project.getId());
    }

    /**
     * 평문 API 키 포맷 사전 검증 — 형식 불일치 시 DB 조회 없이 바로 차단.
     * ApiKeyAuthenticationFilter에서 sha256() 호출 전에 사용할 것.
     */
    public static boolean isValidFormat(String plainKey) {
        return plainKey != null
                && plainKey.startsWith(KEY_PREFIX)
                && plainKey.length() == KEY_LENGTH;
    }
}
