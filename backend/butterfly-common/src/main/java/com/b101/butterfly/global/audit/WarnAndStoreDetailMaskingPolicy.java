package com.b101.butterfly.global.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * prod 환경용 마스킹 정책 — warn-and-store.
 *
 * <p>블랙리스트 키 발견 시 값을 SHA-256 해시 앞 8자로 자동 마스킹하고
 * WARN 로그를 남긴다. 비즈니스 트랜잭션은 중단하지 않는다.
 *
 * <p>WARN 로그는 모니터링 알림 대상으로 설정하여 호출자 코드를 사후 수정할 것.
 */
public class WarnAndStoreDetailMaskingPolicy implements DetailMaskingPolicy {

    private static final Logger log = LoggerFactory.getLogger(WarnAndStoreDetailMaskingPolicy.class);

    @Override
    public Map<String, Object> apply(Map<String, Object> detail) {
        if (detail == null) return null;

        boolean hasSensitiveKey = detail.keySet().stream()
                .anyMatch(MaskingUtil::isSensitiveKey);

        if (!hasSensitiveKey) return detail;

        log.warn("[SECURITY] AuditRecordedEvent.detail에 민감 키가 포함되어 자동 마스킹되었습니다. "
                + "ADR-0008 §비밀값 마스킹 규칙을 확인하고 호출자 코드를 수정하세요. "
                + "keys={}", detail.keySet());

        return MaskingUtil.maskSensitiveKeys(detail);
    }
}
