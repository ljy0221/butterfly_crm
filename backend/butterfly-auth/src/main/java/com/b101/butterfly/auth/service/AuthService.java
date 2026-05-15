package com.b101.butterfly.auth.service;

import com.b101.butterfly.auth.config.JwtProperties;
import com.b101.butterfly.auth.constant.MemberStatus;
import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.dto.request.LoginRequest;
import com.b101.butterfly.auth.dto.request.SetOwnerPasswordRequest;
import com.b101.butterfly.auth.dto.request.SignupRequest;
import com.b101.butterfly.auth.dto.response.LoginResponse;
import com.b101.butterfly.auth.dto.response.MemberInfoResponse;
import com.b101.butterfly.auth.dto.response.SignupResponse;
import com.b101.butterfly.auth.entity.Member;
import com.b101.butterfly.auth.exception.AuthErrorCode;
import com.b101.butterfly.auth.port.CompanyAuthPort;
import com.b101.butterfly.auth.repository.MemberRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

@Service
@Profile("api")
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class AuthService {

    private final MemberRepository memberRepository;
    private final EmailVerificationService emailVerificationService;
    private final RedisService redisService;
    private final PasswordEncoder passwordEncoder;
    private final CompanyAuthPort companyAuthPort;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!emailVerificationService.isVerified(request.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (memberRepository.countByEmailIncludingDeleted(request.email()) > 0) {
            throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL);
        }
        Long projectId = companyAuthPort.findProjectIdByCompanyAuthNo(request.companyAuthNo())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_COMPANY_AUTH_NO));

        Member member = Member.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .phone(request.phone())
                .birthDate(request.birthDate())
                .roleType(RoleType.VIEWER)
                .projectId(projectId)
                .status(MemberStatus.PENDING)
                .build();
        memberRepository.save(member);

        emailVerificationService.clearVerified(request.email());
        return SignupResponse.from(member);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (member.getPasswordHash() == null) {
            throw new BusinessException(AuthErrorCode.PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken  = jwtTokenProvider.createAccessToken(
                member.getId(), member.getProjectId(), member.getRoleType());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        // 재로그인 시 기존 세션 무효화 후 새 토큰 저장 (Redis only)
        redisService.set("auth:refresh:" + member.getId(), refreshToken,
                Duration.ofMillis(jwtProperties.getRefreshTokenExpiryMs()));

        eventPublisher.publishEvent(AuditRecordedEvent.of(
                member.getProjectId(), member.getId(),
                "LOGIN", "Member", member.getId(),
                null, null,
                Map.of("email", member.getEmail(), "role", member.getRoleType().name())
        ));

        return new LoginResponse(accessToken, refreshToken, MemberInfoResponse.from(member));
    }

    @Transactional
    public void logout(Long memberId, String accessToken) {
        // Access Token 블랙리스트 등록
        if (accessToken != null && jwtTokenProvider.isValid(accessToken)) {
            String jti = jwtTokenProvider.getJti(accessToken);
            Duration remaining = jwtTokenProvider.getRemainingTtl(accessToken);
            if (!remaining.isZero()) {
                redisService.set("auth:blacklist:" + jti, "1", remaining);
            }
        }
        // Refresh Token 삭제 (Redis only)
        redisService.delete("auth:refresh:" + memberId);

        Long projectId = (accessToken != null && jwtTokenProvider.isValid(accessToken))
                ? jwtTokenProvider.getProjectId(accessToken) : null;
        eventPublisher.publishEvent(AuditRecordedEvent.of(
                projectId, memberId,
                "LOGOUT", "Member", memberId,
                null, null,
                Map.of()
        ));
    }

    /**
     * Refresh Token Rotation — Redis GETDEL로 동시성 제어.
     *
     * <p>GETDEL은 원자적으로 값을 읽고 삭제한다. 동시 요청이 들어와도 첫 번째 요청만
     * 토큰을 획득하고 두 번째는 null을 받아 즉시 거부되므로 StaleStateException이 발생하지 않는다.
     */
    public LoginResponse refreshToken(String refreshToken) {
        // 1. JWT 서명 및 만료 검증
        if (!jwtTokenProvider.isValid(refreshToken)) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        Long memberId = jwtTokenProvider.getMemberId(refreshToken);

        // 2. Redis GETDEL — 동시 요청 중 첫 번째만 토큰을 가져옴
        String storedToken = redisService.getAndDelete("auth:refresh:" + memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        // 3. 토큰 일치 검증 (재사용 공격 방지)
        if (!storedToken.equals(refreshToken)) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 4. 회원 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        // 5. 새 토큰 발급 및 Redis 저장
        String newAccessToken  = jwtTokenProvider.createAccessToken(
                member.getId(), member.getProjectId(), member.getRoleType());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(member.getId());
        redisService.set("auth:refresh:" + memberId, newRefreshToken,
                Duration.ofMillis(jwtProperties.getRefreshTokenExpiryMs()));

        return new LoginResponse(newAccessToken, newRefreshToken, MemberInfoResponse.from(member));
    }

    @Transactional
    public void setOwnerPassword(SetOwnerPasswordRequest request) {
        String memberIdStr = redisService.get("auth:owner:link:" + request.token())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.OWNER_LINK_EXPIRED));

        Long memberId = Long.parseLong(memberIdStr);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        member.updatePassword(passwordEncoder.encode(request.password()));
        redisService.delete("auth:owner:link:" + request.token());
    }
}
