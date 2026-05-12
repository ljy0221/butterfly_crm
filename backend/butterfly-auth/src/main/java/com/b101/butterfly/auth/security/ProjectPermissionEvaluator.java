package com.b101.butterfly.auth.security;

import com.b101.butterfly.auth.constant.MemberStatus;
import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.entity.Member;
import com.b101.butterfly.auth.repository.MemberRepository;
import com.b101.butterfly.auth.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * ADR-0005: 전 도메인 컨트롤러가 @PreAuthorize 로 참조하는 권한 평가 빈.
 *
 * 사용 예:
 * @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
 */
@Component("projectPermission")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ProjectPermissionEvaluator {

    private static final Duration PERM_TTL = Duration.ofMinutes(5);
    private static final String PERM_KEY_PREFIX = "auth:permission:";

    private final MemberRepository memberRepository;
    private final RedisService redisService;

    /**
     * 현재 인증된 멤버가 projectId에서 required 이상의 역할을 가졌는지 확인.
     * @PreAuthorize SpEL에서 호출됨.
     */
    public boolean hasRole(Long projectId, String requiredRole) {
        Long memberId = currentMemberId();
        if (memberId == null) return false;

        // ADMIN은 projectId=null — 모든 프로젝트 접근 허용
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth.getPrincipal() instanceof CustomUserDetails ud
                && ud.getRoleType() == RoleType.ADMIN) {
            return true;
        }

        try {
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new RuntimeException("Member not found"));
            if (member.getStatus() != MemberStatus.ACTIVE) return false;

            RoleType actual   = resolveRole(memberId, projectId);
            RoleType required = RoleType.valueOf(requiredRole);
            return actual.ordinal() <= required.ordinal();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 역할 변경·멤버 삭제 시 캐시 즉시 무효화.
     */
    public void evictCache(Long memberId, Long projectId) {
        redisService.delete(permKey(memberId, projectId));
    }

    private RoleType resolveRole(Long memberId, Long projectId) {
        String key = permKey(memberId, projectId);

        return redisService.get(key)
                .map(RoleType::valueOf)
                .orElseGet(() -> {
                    Member member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new RuntimeException("Member not found"));

                    if (!projectId.equals(member.getProjectId())) {
                        throw new RuntimeException("Member not in project");
                    }

                    RoleType role = member.getRoleType();
                    redisService.set(key, role.name(), PERM_TTL);
                    return role;
                });
    }

    private Long currentMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getMemberId();
        }
        return null;
    }

    private String permKey(Long memberId, Long projectId) {
        return PERM_KEY_PREFIX + memberId + ":" + projectId;
    }
}
