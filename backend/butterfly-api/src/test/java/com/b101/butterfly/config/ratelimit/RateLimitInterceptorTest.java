package com.b101.butterfly.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("RateLimitInterceptor")
class RateLimitInterceptorTest {

    private static final int TOO_MANY_REQUESTS = 429;

    private RateLimiter rateLimiter;
    private RateLimitProperties properties;
    private RateLimitInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RateLimiter.class);
        properties = new RateLimitProperties(true, 60, 100);
        interceptor = new RateLimitInterceptor(rateLimiter, properties);
        request = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("enabled=false → 무조건 통과, RateLimiter 호출 안 함")
    void disabledPassesThrough() throws Exception {
        properties = new RateLimitProperties(false, 60, 100);
        interceptor = new RateLimitInterceptor(rateLimiter, properties);

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("허용된 요청 → 통과 + X-RateLimit 헤더 설정")
    void allowedSetsHeaders() throws Exception {
        request.setRemoteAddr("1.2.3.4");
        given(rateLimiter.tryAcquire(eq("ip:1.2.3.4")))
                .willReturn(new RateLimiter.Decision(true, 100, 42, 0L));

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isTrue();
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("42");
    }

    @Test
    @DisplayName("임계값 초과 → 429 + Retry-After 헤더")
    void exceededReturns429() throws Exception {
        request.setRemoteAddr("1.2.3.4");
        given(rateLimiter.tryAcquire(eq("ip:1.2.3.4")))
                .willReturn(new RateLimiter.Decision(false, 100, 0, 30L));

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isFalse();
        assertThat(response.getStatus()).isEqualTo(TOO_MANY_REQUESTS);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    @DisplayName("인증된 사용자 → memberId 스코프 사용")
    void authenticatedUsesMemberScope() throws Exception {
        CustomUserDetails user = new CustomUserDetails(42L, 7L, RoleType.OWNER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        given(rateLimiter.tryAcquire(eq("member:42")))
                .willReturn(new RateLimiter.Decision(true, 100, 99, 0L));

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("X-Forwarded-For 첫 IP를 스코프로 사용")
    void xForwardedForFirstHop() throws Exception {
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        given(rateLimiter.tryAcquire(eq("ip:10.0.0.1")))
                .willReturn(new RateLimiter.Decision(true, 100, 50, 0L));

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isTrue();
    }
}
