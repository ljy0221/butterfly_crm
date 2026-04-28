package com.b101.butterfly.global.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 단위 추적 ID(MDC) 세팅 필터.
 *
 * <p>{@code X-Request-Id} 헤더가 있으면 그대로 쓰고, 없으면 UUID를 발급한다.
 * 응답 헤더에도 동일 값을 실어 클라이언트와 서버 로그가 같은 ID로 연결된다.
 *
 * <p>logback 패턴에서 {@code %X{requestId}} 로 참조한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(MdcKeys.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        response.setHeader(MdcKeys.REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.REQUEST_ID);
            MDC.remove(MdcKeys.PROJECT_ID);
            MDC.remove(MdcKeys.MEMBER_ID);
            MDC.remove(MdcKeys.TRACE_ID);
        }
    }
}
