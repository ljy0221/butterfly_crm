package com.b101.butterfly.config.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("IdempotencyKeyInterceptor")
class IdempotencyKeyInterceptorTest {

    private static final int UNPROCESSABLE_ENTITY = 422;

    private IdempotencyStore store;
    private IdempotencyKeyInterceptor interceptor;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        store = mock(IdempotencyStore.class);
        interceptor = new IdempotencyKeyInterceptor(store);
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("GET 요청은 헤더 무시하고 통과")
    void getMethodPasses() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/projects/1/campaigns");
        req.addHeader("Idempotency-Key", "any-key-value");

        boolean ok = interceptor.preHandle(req, response, new Object());

        assertThat(ok).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("POST + 헤더 없음 → 통과 (기존 동작 보존)")
    void postWithoutHeaderPasses() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");

        boolean ok = interceptor.preHandle(req, response, new Object());

        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("POST + 헤더 + lock 점유 성공 → 통과")
    void postWithHeaderAcquiresLock() throws Exception {
        CachedBodyHttpServletRequest req = withBody(
                "POST", "/api/v1/projects/1/campaigns",
                "{\"name\":\"foo\"}", "valid-key-12345");
        given(store.tryLockWithFingerprint(anyString(), anyString(), any()))
                .willReturn(IdempotencyStore.LockOutcome.ACQUIRED);

        boolean ok = interceptor.preHandle(req, response, new Object());

        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("PATCH + 같은 키 + 같은 본문 → 409 Conflict")
    void duplicateKeyConflict() throws Exception {
        CachedBodyHttpServletRequest req = withBody(
                "PATCH", "/api/v1/projects/1/campaigns/2/status",
                "{\"status\":\"ACTIVE\"}", "valid-key-12345");
        given(store.tryLockWithFingerprint(anyString(), anyString(), any()))
                .willReturn(IdempotencyStore.LockOutcome.FINGERPRINT_MATCH);

        boolean ok = interceptor.preHandle(req, response, new Object());

        assertThat(ok).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    @DisplayName("PATCH + 같은 키 + 다른 본문 → 422 Unprocessable Entity")
    void keyReusedWithDifferentBody() throws Exception {
        CachedBodyHttpServletRequest req = withBody(
                "PATCH", "/api/v1/projects/1/campaigns/2/status",
                "{\"status\":\"PAUSED\"}", "valid-key-12345");
        given(store.tryLockWithFingerprint(anyString(), anyString(), any()))
                .willReturn(IdempotencyStore.LockOutcome.FINGERPRINT_MISMATCH);

        boolean ok = interceptor.preHandle(req, response, new Object());

        assertThat(ok).isFalse();
        assertThat(response.getStatus()).isEqualTo(UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("Idempotency-Key 길이 < 8 → 400")
    void keyTooShort() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        req.addHeader("Idempotency-Key", "short");

        boolean ok = interceptor.preHandle(req, response, new Object());

        assertThat(ok).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Idempotency-Key 길이 > 255 → 400")
    void keyTooLong() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/api/v1/projects/1/templates/1");
        req.addHeader("Idempotency-Key", "a".repeat(256));

        boolean ok = interceptor.preHandle(req, response, new Object());

        assertThat(ok).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    // ── afterCompletion 테스트 ──────────────────────────────────────────────

    @Test
    @DisplayName("afterCompletion: 200 응답 → release 호출 안 함")
    void afterCompletion_200_noRelease() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        req.addHeader("Idempotency-Key", "valid-key-12345");
        response.setStatus(HttpStatus.OK.value());

        interceptor.afterCompletion(req, response, new Object(), null);

        verify(store, never()).release(anyString());
    }

    @Test
    @DisplayName("afterCompletion: 201 응답 → release 호출 안 함")
    void afterCompletion_201_noRelease() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        req.addHeader("Idempotency-Key", "valid-key-12345");
        response.setStatus(HttpStatus.CREATED.value());

        interceptor.afterCompletion(req, response, new Object(), null);

        verify(store, never()).release(anyString());
    }

    @Test
    @DisplayName("afterCompletion: 400 응답 → release 호출 안 함 (4xx lock 유지)")
    void afterCompletion_400_noRelease() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        req.addHeader("Idempotency-Key", "valid-key-12345");
        response.setStatus(HttpStatus.BAD_REQUEST.value());

        interceptor.afterCompletion(req, response, new Object(), null);

        verify(store, never()).release(anyString());
    }

    @Test
    @DisplayName("afterCompletion: 500 응답 → release 1회 호출")
    void afterCompletion_500_release() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        req.addHeader("Idempotency-Key", "valid-key-12345");
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        interceptor.afterCompletion(req, response, new Object(), null);

        verify(store).release("valid-key-12345");
    }

    @Test
    @DisplayName("afterCompletion: 503 응답 → release 1회 호출")
    void afterCompletion_503_release() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        req.addHeader("Idempotency-Key", "valid-key-12345");
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());

        interceptor.afterCompletion(req, response, new Object(), null);

        verify(store).release("valid-key-12345");
    }

    @Test
    @DisplayName("afterCompletion: Idempotency-Key 헤더 없음 → release 호출 안 함")
    void afterCompletion_noHeader_noRelease() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        interceptor.afterCompletion(req, response, new Object(), null);

        verify(store, never()).release(anyString());
    }

    private static CachedBodyHttpServletRequest withBody(String method, String uri,
                                                         String body, String idempotencyKey)
            throws java.io.IOException {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setContentType("application/json");
        req.setContent(body.getBytes());
        req.addHeader("Idempotency-Key", idempotencyKey);
        return new CachedBodyHttpServletRequest(req);
    }
}
