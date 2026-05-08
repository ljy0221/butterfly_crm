package com.b101.butterfly.config.wiring;

import com.b101.butterfly.reporting.event.port.ApiKeyInfo;
import com.b101.butterfly.reporting.event.port.ApiKeyValidator;
import com.b101.butterfly.tenant.repository.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * butterfly-api 조립 레이어에서 reporting/event 의 SPI({@link ApiKeyValidator})를
 * tenant {@code ProjectRepository.findByPublicApiKey} 로 충족시키는 어댑터.
 *
 * <p>합의 (2026-05-08, 김동현, S14P31B101-193): 옵션 A — Bean SPI. DB 단일 진실 공급원,
 * Redis 캐시 미사용. 187의 {@code CampaignExecutionPortAdapter}와 동일한 wiring 패턴
 * (BC 격리: reporting/event → tenant 직접 의존 없음, api 모듈에서만 wiring).
 *
 * <p>키 포맷: {@code pk_live_} 8자 + UUID hex 24자 = 32자 고정. DB 저장은 SHA-256 해시
 * 64자이므로 평문 키를 동일 알고리즘({@code ApiKeyService.sha256()} 과 정확히 일치 —
 * SHA-256, UTF-8, hex lowercase)으로 해싱한 뒤 조회한다.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyValidatorAdapter implements ApiKeyValidator {

    private static final String KEY_PREFIX = "pk_live_";
    private static final int KEY_LENGTH = 32;

    private final ProjectRepository projectRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiKeyInfo> validate(String rawKey) {
        if (rawKey == null
                || rawKey.length() != KEY_LENGTH
                || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String hash = sha256(rawKey);
        return projectRepository.findByPublicApiKey(hash)
                .map(p -> new ApiKeyInfo(p.getId()));
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 해싱 실패", e);
        }
    }
}
