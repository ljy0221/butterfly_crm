package com.b101.butterfly.reporting.notification.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 내부 엔드포인트(`/internal/**`)용 토큰 검증 가드.
 *
 * <p>운영에서는 게이트웨이 + 네트워크 정책으로 외부 노출이 차단되지만, 본 가드는 두 번째
 * 방어선으로 동작한다. 호출자는 `X-Internal-Token` 헤더에 사전 공유된 토큰을 실어야 한다.
 *
 * <p>현재 인증 모듈이 미완성이라 컨트롤러 레벨에서 직접 검증한다. 인증 모듈이 도입되면
 * SecurityFilterChain 측 필터로 옮길 것 — 컨트롤러는 그때 검증 코드를 제거한다.
 */
@Component
public class InternalTokenGuard {

    private final String configuredToken;

    public InternalTokenGuard(@Value("${butterfly.internal.token:}") String configuredToken) {
        this.configuredToken = configuredToken;
    }

    /**
     * @throws ResponseStatusException 토큰이 없거나 일치하지 않으면 401 UNAUTHORIZED
     */
    public void verify(String headerToken) {
        if (configuredToken == null || configuredToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "internal token not configured"
            );
        }
        if (headerToken == null || !configuredToken.equals(headerToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }
}
