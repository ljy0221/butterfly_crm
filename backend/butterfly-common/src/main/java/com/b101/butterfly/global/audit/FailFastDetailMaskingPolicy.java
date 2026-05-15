package com.b101.butterfly.global.audit;

import java.util.Map;

/**
 * dev / CI 환경용 마스킹 정책 — fail-fast.
 *
 * <p>블랙리스트 키 발견 즉시 {@link IllegalArgumentException}을 발생시켜
 * 트랜잭션을 롤백한다. 개발 단계에서 평문 노출을 조기에 차단하는 것이 목적이다.
 */
public class FailFastDetailMaskingPolicy implements DetailMaskingPolicy {

    @Override
    public Map<String, Object> apply(Map<String, Object> detail) {
        if (detail == null) return null;
        for (String key : detail.keySet()) {
            if (MaskingUtil.isSensitiveKey(key)) {
                throw new IllegalArgumentException(
                        "AuditRecordedEvent.detail에 민감 키가 포함되었습니다: '" + key + "'. "
                        + "ADR-0008 §비밀값 마스킹 규칙을 확인하고 MaskingUtil.maskValue()를 사용하세요."
                );
            }
        }
        return detail;
    }
}
