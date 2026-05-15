package com.b101.butterfly.config;

import com.b101.butterfly.global.audit.FailFastDetailMaskingPolicy;
import com.b101.butterfly.global.audit.MaskingPolicyHolder;
import com.b101.butterfly.global.audit.WarnAndStoreDetailMaskingPolicy;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * AuditRecordedEvent 비밀값 마스킹 정책 설정 (ADR-0008).
 *
 * <p>환경별 정책을 {@link MaskingPolicyHolder}에 등록한다.
 * <ul>
 *   <li>prod  — {@link WarnAndStoreDetailMaskingPolicy}: 자동 마스킹 + WARN 로그 (운영 장애 방지)
 *   <li>!prod — {@link FailFastDetailMaskingPolicy}: 예외 발생 → 트랜잭션 롤백 (개발 단계 조기 발견)
 * </ul>
 */
public class AuditMaskingConfig {

    @Configuration
    @Profile("prod")
    static class ProdPolicy {
        @PostConstruct
        void configure() {
            MaskingPolicyHolder.configure(new WarnAndStoreDetailMaskingPolicy());
        }
    }

    @Configuration
    @Profile("!prod")
    static class DevPolicy {
        @PostConstruct
        void configure() {
            MaskingPolicyHolder.configure(new FailFastDetailMaskingPolicy());
        }
    }
}
