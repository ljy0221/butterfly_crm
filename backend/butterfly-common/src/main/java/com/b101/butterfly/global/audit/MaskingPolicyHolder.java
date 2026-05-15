package com.b101.butterfly.global.audit;

import java.util.Map;

/**
 * 마스킹 정책 정적 홀더.
 *
 * <p>{@link AuditRecordedEvent} canonical constructor는 Spring 컨텍스트 없이 동작하므로
 * 정책을 정적 필드로 보관한다. 초기화는 Spring 설정 클래스(AuditMaskingConfig)에서 수행한다.
 *
 * <p><b>주의</b>: {@link #configure}는 AuditMaskingConfig에서만 호출해야 한다.
 * 도메인 서비스에서 직접 호출하면 ArchUnit 규칙 위반이다.
 */
public final class MaskingPolicyHolder {

    // prod 배포 전 configure()가 호출되기 전 안전한 기본값 — warn-and-store
    private static volatile DetailMaskingPolicy policy = new WarnAndStoreDetailMaskingPolicy();

    private MaskingPolicyHolder() {}

    /**
     * 환경에 맞는 정책을 등록한다. Spring 설정 클래스에서만 호출.
     */
    public static void configure(DetailMaskingPolicy p) {
        policy = p;
    }

    /**
     * 현재 정책을 적용하여 detail Map을 반환한다.
     * AuditRecordedEvent canonical constructor에서만 호출한다.
     */
    public static Map<String, Object> apply(Map<String, Object> detail) {
        return policy.apply(detail);
    }
}
