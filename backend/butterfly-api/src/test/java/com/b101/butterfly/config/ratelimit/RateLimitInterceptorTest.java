package com.b101.butterfly.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.security.CustomUserDetails;
import java.util.Map;
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
        properties = new RateLimitProperties(true, 60, 100, null);
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
        properties = new RateLimitProperties(false, 60, 100, null);
        interceptor = new RateLimitInterceptor(rateLimiter, properties);

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("허용된 요청 → 통과 + X-RateLimit 헤더 설정")
    void allowedSetsHeaders() throws Exception {
        request.setRemoteAddr("1.2.3.4");
        given(rateLimiter.tryAcquire(eq("ip:1.2.3.4"), anyInt()))
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
        given(rateLimiter.tryAcquire(eq("ip:1.2.3.4"), anyInt()))
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
        given(rateLimiter.tryAcquire(eq("member:42"), anyInt()))
                .willReturn(new RateLimiter.Decision(true, 100, 99, 0L));

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("X-Forwarded-For 첫 IP를 스코프로 사용")
    void xForwardedForFirstHop() throws Exception {
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        given(rateLimiter.tryAcquire(eq("ip:10.0.0.1"), anyInt()))
                .willReturn(new RateLimiter.Decision(true, 100, 50, 0L));

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("PATCH status endpoint → limit=10 으로 tryAcquire 호출")
    void statusEndpointUsesLowLimit() throws Exception {
        Map<String, Integer> limits = Map.of("PATCH:/api/v1/projects/*/campaigns/*/status", 10);
        properties = new RateLimitProperties(true, 60, 100, limits);
        interceptor = new RateLimitInterceptor(rateLimiter, properties);
        request = new MockHttpServletRequest("PATCH", "/api/v1/projects/1/campaigns/5/status");
        request.setRemoteAddr("1.2.3.4");
        given(rateLimiter.tryAcquire(eq("ip:1.2.3.4"), eq(10)))
                .willReturn(new RateLimiter.Decision(true, 10, 9, 0L));

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isTrue();
        verify(rateLimiter).tryAcquire("ip:1.2.3.4", 10);
    }

    @Test
    @DisplayName("PATCH status endpoint — limit 10 초과 → 429")
    void statusEndpointExceedsLowLimit() throws Exception {
        Map<String, Integer> limits = Map.of("PATCH:/api/v1/projects/*/campaigns/*/status", 10);
        properties = new RateLimitProperties(true, 60, 100, limits);
        interceptor = new RateLimitInterceptor(rateLimiter, properties);
        request = new MockHttpServletRequest("PATCH", "/api/v1/projects/1/campaigns/5/status");
        request.setRemoteAddr("1.2.3.4");
        given(rateLimiter.tryAcquire(eq("ip:1.2.3.4"), eq(10)))
                .willReturn(new RateLimiter.Decision(false, 10, 0, 45L));

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isFalse();
        assertThat(response.getStatus()).isEqualTo(TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("GET campaigns endpoint → limit=200 으로 tryAcquire 호출")
    void getCampaignsEndpointUsesHighLimit() throws Exception {
        Map<String, Integer> limits = Map.of("GET:/api/v1/projects/*/campaigns/**", 200);
        properties = new RateLimitProperties(true, 60, 100, limits);
        interceptor = new RateLimitInterceptor(rateLimiter, properties);
        request = new MockHttpServletRequest("GET", "/api/v1/projects/1/campaigns/5");
        request.setRemoteAddr("1.2.3.4");
        given(rateLimiter.tryAcquire(eq("ip:1.2.3.4"), eq(200)))
                .willReturn(new RateLimiter.Decision(true, 200, 199, 0L));

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isTrue();
        verify(rateLimiter).tryAcquire("ip:1.2.3.4", 200);
    }

    @Test
    @DisplayName("매칭 없는 endpoint → default burstCapacity(100) 사용")
    void unmatchedEndpointUsesDefaultBurstCapacity() throws Exception {
        Map<String, Integer> limits = Map.of("POST:/api/v1/projects/*/campaigns", 30);
        properties = new RateLimitProperties(true, 60, 100, limits);
        interceptor = new RateLimitInterceptor(rateLimiter, properties);
        request = new MockHttpServletRequest("GET", "/api/v1/projects/1/members");
        request.setRemoteAddr("1.2.3.4");
        given(rateLimiter.tryAcquire(eq("ip:1.2.3.4"), eq(100)))
                .willReturn(new RateLimiter.Decision(true, 100, 99, 0L));

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isTrue();
        verify(rateLimiter).tryAcquire("ip:1.2.3.4", 100);
    }

    @Test
    @DisplayName("ant-pattern 와일드카드 ** 매칭 — 중첩 경로도 매칭")
    void antPatternDoubleWildcardMatchesNestedPaths() throws Exception {
        Map<String, Integer> limits = Map.of("GET:/api/v1/projects/*/campaigns/**", 200);
        properties = new RateLimitProperties(true, 60, 100, limits);
        interceptor = new RateLimitInterceptor(rateLimiter, properties);
        // 중첩 경로 /campaigns/5/executions/3 도 ** 에 매칭되어야 함
        request = new MockHttpServletRequest("GET", "/api/v1/projects/1/campaigns/5/executions/3");
        request.setRemoteAddr("1.2.3.4");
        given(rateLimiter.tryAcquire(eq("ip:1.2.3.4"), eq(200)))
                .willReturn(new RateLimiter.Decision(true, 200, 190, 0L));

        boolean ok = interceptor.preHandle(request, response, new Object());

        assertThat(ok).isTrue();
        verify(rateLimiter).tryAcquire("ip:1.2.3.4", 200);
    }
}
