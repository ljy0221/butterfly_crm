package com.b101.butterfly.config.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

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
    @DisplayName("PATCH + 같은 키 + 같은 본문 + 캐시 없음 → 409 Conflict")
    void duplicateKeyConflictWithoutCache() throws Exception {
        CachedBodyHttpServletRequest req = withBody(
                "PATCH", "/api/v1/projects/1/campaigns/2/status",
                "{\"status\":\"ACTIVE\"}", "valid-key-12345");
        given(store.tryLockWithFingerprint(anyString(), anyString(), any()))
                .willReturn(IdempotencyStore.LockOutcome.FINGERPRINT_MATCH);
        given(store.findResponse(anyString())).willReturn(Optional.empty());

        boolean ok = interceptor.preHandle(req, response, new Object());

        assertThat(ok).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    @DisplayName("PATCH + 같은 키 + 같은 본문 + 캐시 있음 → 캐시 응답 반환 (Phase 4)")
    void duplicateKeyServesCachedResponse() throws Exception {
        CachedBodyHttpServletRequest req = withBody(
                "PATCH", "/api/v1/projects/1/campaigns/2/status",
                "{\"status\":\"ACTIVE\"}", "valid-key-12345");
        given(store.tryLockWithFingerprint(anyString(), anyString(), any()))
                .willReturn(IdempotencyStore.LockOutcome.FINGERPRINT_MATCH);
        // 첫 요청에서 저장된 fingerprint를 받기 위해 같은 본문으로 미리 계산해 둠.
        String fingerprint = computeFingerprint("{\"status\":\"ACTIVE\"}");
        byte[] body = "{\"data\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        CachedResponse cached = new CachedResponse(200, "application/json", body, fingerprint);
        given(store.findResponse(eq("valid-key-12345"))).willReturn(Optional.of(cached));

        boolean ok = interceptor.preHandle(req, response, new Object());

        assertThat(ok).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsByteArray()).isEqualTo(body);
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
    @DisplayName("afterCompletion: 200 응답 + 응답 wrapper → saveResponse 호출, release 미호출")
    void afterCompletion_200_savesResponse() throws Exception {
        CachedBodyHttpServletRequest req = withBody(
                "POST", "/api/v1/projects/1/campaigns", "{\"name\":\"foo\"}", "valid-key-12345");
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        wrapped.setStatus(HttpStatus.OK.value());
        wrapped.setContentType("application/json");
        wrapped.getWriter().write("{\"data\":\"ok\"}");
        wrapped.getWriter().flush();

        interceptor.afterCompletion(req, wrapped, new Object(), null);

        verify(store, never()).release(anyString());
        verify(store).saveResponse(eq("valid-key-12345"), any(CachedResponse.class), any());
    }

    @Test
    @DisplayName("afterCompletion: 201 응답 → saveResponse 호출, release 미호출")
    void afterCompletion_201_savesResponse() throws Exception {
        CachedBodyHttpServletRequest req = withBody(
                "POST", "/api/v1/projects/1/campaigns", "{\"name\":\"foo\"}", "valid-key-12345");
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        wrapped.setStatus(HttpStatus.CREATED.value());

        interceptor.afterCompletion(req, wrapped, new Object(), null);

        verify(store, never()).release(anyString());
        verify(store).saveResponse(eq("valid-key-12345"), any(CachedResponse.class), any());
    }

    @Test
    @DisplayName("afterCompletion: 400 응답 → saveResponse 호출 (4xx도 결정적이므로 캐시), release 미호출")
    void afterCompletion_400_savesResponse() throws Exception {
        CachedBodyHttpServletRequest req = withBody(
                "POST", "/api/v1/projects/1/campaigns", "{\"name\":\"foo\"}", "valid-key-12345");
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        wrapped.setStatus(HttpStatus.BAD_REQUEST.value());

        interceptor.afterCompletion(req, wrapped, new Object(), null);

        verify(store, never()).release(anyString());
        verify(store).saveResponse(eq("valid-key-12345"), any(CachedResponse.class), any());
    }

    @Test
    @DisplayName("afterCompletion: 500 응답 → release 1회 호출, saveResponse 미호출")
    void afterCompletion_500_release() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        req.addHeader("Idempotency-Key", "valid-key-12345");
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        interceptor.afterCompletion(req, response, new Object(), null);

        verify(store).release("valid-key-12345");
        verify(store, never()).saveResponse(anyString(), any(), any());
    }

    @Test
    @DisplayName("afterCompletion: 503 응답 → release 1회 호출")
    void afterCompletion_503_release() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        req.addHeader("Idempotency-Key", "valid-key-12345");
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());

        interceptor.afterCompletion(req, response, new Object(), null);

        verify(store).release("valid-key-12345");
        verify(store, never()).saveResponse(anyString(), any(), any());
    }

    @Test
    @DisplayName("afterCompletion: Idempotency-Key 헤더 없음 → release/save 모두 미호출")
    void afterCompletion_noHeader_noOp() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        interceptor.afterCompletion(req, response, new Object(), null);

        verify(store, never()).release(anyString());
        verify(store, never()).saveResponse(anyString(), any(), any());
    }

    @Test
    @DisplayName("afterCompletion: 응답이 ContentCachingResponseWrapper로 감싸지지 않으면 saveResponse 미호출")
    void afterCompletion_unwrapped_skipsSave() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        req.addHeader("Idempotency-Key", "valid-key-12345");
        response.setStatus(HttpStatus.OK.value());

        interceptor.afterCompletion(req, response, new Object(), null);

        verify(store, never()).saveResponse(anyString(), any(), any());
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

    private static String computeFingerprint(String body) throws java.security.NoSuchAlgorithmException {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
