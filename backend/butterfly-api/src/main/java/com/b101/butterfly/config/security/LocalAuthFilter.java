package com.b101.butterfly.config.security;

import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 로컬/개발 환경 전용 인증 필터 (prod 프로파일 제외).
 *
 * <p>JWT 없이 {@code X-Member-Id} 헤더만으로 SecurityContext를 채워
 * {@code @AuthenticationPrincipal}이 정상 동작하게 한다.
 * prod에서는 {@link JwtAuthenticationFilter}가 동일 역할 수행.
 *
 * <p>사용 가능한 헤더:
 * <ul>
 *   <li>{@code X-Member-Id} — 행위자 회원 ID (필수)</li>
 *   <li>{@code X-Project-Id} — 프로젝트 ID (선택, 기본값 null)</li>
 *   <li>{@code X-Role} — 역할 ADMIN/OWNER/EDITOR/VIEWER (선택, 기본값 VIEWER)</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("!prod")
public class LocalAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String memberIdHeader = req.getHeader("X-Member-Id");
        if (memberIdHeader != null) {
            try {
                Long memberId  = Long.parseLong(memberIdHeader);
                String projectH = req.getHeader("X-Project-Id");
                Long projectId  = projectH != null ? Long.parseLong(projectH) : null;
                String roleH   = req.getHeader("X-Role");
                RoleType role  = roleH != null ? RoleType.valueOf(roleH) : RoleType.VIEWER;

                CustomUserDetails principal = new CustomUserDetails(memberId, projectId, role);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities())
                );
            } catch (Exception e) {
                log.debug("LocalAuthFilter: 헤더 파싱 실패 — 인증 미설정. reason={}", e.getMessage());
            }
        }
        chain.doFilter(req, res);
    }
}
