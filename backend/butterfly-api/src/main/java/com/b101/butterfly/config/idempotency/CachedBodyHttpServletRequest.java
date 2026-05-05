package com.b101.butterfly.config.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 본문을 메모리에 한 번 읽어 캐시한 뒤 여러 번 다시 읽을 수 있는 요청 래퍼.
 *
 * <p>{@link org.springframework.web.util.ContentCachingRequestWrapper}는 stream을 한 번 읽으면
 * underlying stream이 소진되어 controller가 빈 body를 보게 된다. 이 래퍼는 생성 시 즉시
 * 전체 본문을 버퍼에 담아 {@link #getInputStream()}/{@link #getReader()} 호출 시마다
 * 새 {@link ByteArrayInputStream}을 반환한다.
 *
 * <p>Idempotency-Key Phase 3 fingerprint 계산 → 컨트롤러 {@code @RequestBody} 파싱 양쪽이
 * 같은 바이트를 읽도록 보장하기 위해 사용한다.
 *
 * <p>대용량 본문은 Phase 3 보호의 비용 (메모리)이지만, Idempotency-Key 헤더가 있을 때만
 * 적용되므로 일반 트래픽에는 영향 없다. {@code Pageable max-page-size=100} + 본문 상한
 * (DTO @Size) 정책과 결합해 안전 범위 내.
 */
public final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        try (var in = request.getInputStream()) {
            this.cachedBody = in.readAllBytes();
        }
    }

    public byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("async read not supported");
            }

            @Override
            public int read() {
                return byteStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        Charset charset = resolveCharset();
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    private Charset resolveCharset() {
        String encoding = getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }
}
