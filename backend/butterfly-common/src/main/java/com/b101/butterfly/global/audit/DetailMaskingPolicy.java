package com.b101.butterfly.global.audit;

import java.util.Map;

/**
 * AuditRecordedEvent.detail 마스킹 정책 인터페이스.
 *
 * <p>환경별로 다른 구현체를 사용한다:
 * <ul>
 *   <li>prod  — {@link WarnAndStoreDetailMaskingPolicy}: 자동 마스킹 + WARN 로그
 *   <li>!prod — {@link FailFastDetailMaskingPolicy}: 예외 발생 → 트랜잭션 롤백
 * </ul>
 *
 * <p>구현체는 {@link MaskingPolicyHolder}를 통해 등록하며,
 * Spring 설정 클래스(AuditMaskingConfig)에서만 호출해야 한다.
 */
public interface DetailMaskingPolicy {

    /**
     * detail Map을 검사하고 정책에 따라 처리한 결과를 반환한다.
     *
     * @param detail 원본 detail Map
     * @return 정책 적용 후 Map (fail-fast의 경우 예외 발생)
     */
    Map<String, Object> apply(Map<String, Object> detail);
}
