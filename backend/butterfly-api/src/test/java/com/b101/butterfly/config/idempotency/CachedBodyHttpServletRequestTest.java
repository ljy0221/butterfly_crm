package com.b101.butterfly.config.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("CachedBodyHttpServletRequest")
class CachedBodyHttpServletRequestTest {

    @Test
    @DisplayName("getInputStream을 두 번 호출해도 같은 본문을 반환한다 (interceptor + controller 시나리오)")
    void inputStreamRepeatable() throws Exception {
        MockHttpServletRequest underlying = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        underlying.setContent("{\"name\":\"foo\"}".getBytes(StandardCharsets.UTF_8));
        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(underlying);

        byte[] firstRead = wrapper.getInputStream().readAllBytes();
        byte[] secondRead = wrapper.getInputStream().readAllBytes();

        assertThat(firstRead).isEqualTo(secondRead);
        assertThat(new String(firstRead, StandardCharsets.UTF_8)).isEqualTo("{\"name\":\"foo\"}");
    }

    @Test
    @DisplayName("getCachedBody는 underlying stream과 같은 바이트를 반환한다")
    void cachedBodyMatchesOriginal() throws Exception {
        byte[] original = "{\"status\":\"ACTIVE\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest underlying = new MockHttpServletRequest("PATCH", "/api/v1/projects/1/campaigns/2/status");
        underlying.setContent(original);

        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(underlying);

        assertThat(wrapper.getCachedBody()).isEqualTo(original);
    }

    @Test
    @DisplayName("빈 본문도 안전 처리 — getCachedBody 길이 0")
    void emptyBody() throws Exception {
        MockHttpServletRequest underlying = new MockHttpServletRequest("DELETE", "/api/v1/projects/1/templates/1");
        underlying.setContent(new byte[0]);

        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(underlying);

        assertThat(wrapper.getCachedBody()).hasSize(0);
        assertThat(wrapper.getInputStream().readAllBytes()).hasSize(0);
    }

    @Test
    @DisplayName("getReader도 같은 본문을 반환 — 컨트롤러 reader 경로 보호")
    void readerReturnsCachedBody() throws Exception {
        String body = "{\"key\":\"value\"}";
        MockHttpServletRequest underlying = new MockHttpServletRequest("POST", "/api/v1/projects/1/campaigns");
        underlying.setContent(body.getBytes(StandardCharsets.UTF_8));
        underlying.setCharacterEncoding("UTF-8");

        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(underlying);

        String read = wrapper.getReader().lines().reduce("", String::concat);
        assertThat(read).isEqualTo(body);
    }
}
