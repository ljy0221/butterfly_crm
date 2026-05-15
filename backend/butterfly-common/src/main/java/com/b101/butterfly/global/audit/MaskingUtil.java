package com.b101.butterfly.global.audit;

import com.b101.butterfly.global.util.HashUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 감사 로그 비밀값 마스킹 유틸리티 — 단일 진실원.
 *
 * <p>ADR-0008 §"비밀값 마스킹 규칙" 구현체.
 * 블랙리스트 키를 substring 포함 방식으로 검사하고,
 * 탐지된 값은 SHA-256 해시 앞 8자 + "..." 로 대체한다.
 *
 * <p><b>블랙리스트 매칭 방식</b>: 키 이름에 블랙리스트 패턴이 포함되면 차단.
 * 예) "userPassword", "newPasswordHash" → 차단 / "projectId" → 통과
 */
public final class MaskingUtil {

    /**
     * 민감 키 패턴 목록 (소문자 substring 매칭).
     * 추가/제거 시 ADR-0008 및 2026-05-15-audit-masking-decision.md 참고.
     */
    static final Set<String> BLACKLIST_PATTERNS = Set.of(
            "password", "secret", "credential",
            "apikey", "token", "privatekey",
            "otp", "code", "pin", "cvv"
    );

    private MaskingUtil() {}

    /**
     * 키가 민감 정보 패턴에 해당하는지 검사한다.
     */
    public static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase(Locale.ROOT);
        return BLACKLIST_PATTERNS.stream().anyMatch(lower::contains);
    }

    /**
     * 값을 SHA-256 해시 앞 8자 + "..." 로 마스킹한다.
     */
    public static String maskValue(Object value) {
        if (value == null) return "***";
        return HashUtils.sha256(value.toString()).substring(0, 8) + "...";
    }

    /**
     * detail Map에서 민감 키의 값을 마스킹한 새 Map을 반환한다.
     * 원본 Map은 변경하지 않는다.
     */
    public static Map<String, Object> maskSensitiveKeys(Map<String, Object> detail) {
        if (detail == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : detail.entrySet()) {
            if (isSensitiveKey(entry.getKey())) {
                result.put(entry.getKey(), maskValue(entry.getValue()));
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
