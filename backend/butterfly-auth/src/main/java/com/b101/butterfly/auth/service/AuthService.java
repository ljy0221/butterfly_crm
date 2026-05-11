package com.b101.butterfly.auth.service;

import com.b101.butterfly.auth.config.JwtProperties;
import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.dto.request.LoginRequest;
import com.b101.butterfly.auth.dto.request.SetOwnerPasswordRequest;
import com.b101.butterfly.auth.dto.request.SignupRequest;
import com.b101.butterfly.auth.dto.response.LoginResponse;
import com.b101.butterfly.auth.dto.response.MemberInfoResponse;
import com.b101.butterfly.auth.dto.response.SignupResponse;
import com.b101.butterfly.auth.entity.Member;
import com.b101.butterfly.auth.entity.RefreshToken;
import com.b101.butterfly.auth.exception.AuthErrorCode;
import com.b101.butterfly.auth.port.CompanyAuthPort;
import com.b101.butterfly.auth.repository.MemberRepository;
import com.b101.butterfly.auth.repository.RefreshTokenRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class AuthService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
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

        // 기존 Refresh Token 삭제 (재로그인 시 갱신)
        refreshTokenRepository.deleteByMemberId(member.getId());
        redisService.delete("auth:refresh:" + member.getId());

        // 토큰 발급
        String accessToken  = jwtTokenProvider.createAccessToken(
                member.getId(), member.getProjectId(), member.getRoleType());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        // Refresh Token 저장 (DB + Redis)
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtProperties.getRefreshTokenExpiryMs() / 1000);
        refreshTokenRepository.save(RefreshToken.builder()
                .memberId(member.getId())
                .token(refreshToken)
                .expiresAt(expiresAt)
                .build());
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
        // Refresh Token 삭제
        refreshTokenRepository.deleteByMemberId(memberId);
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

    @Transactional
    public LoginResponse refreshToken(String refreshToken) {
        // Redis 우선 조회, 없으면 DB 조회
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        Long memberId = stored.getMemberId();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        // RTR: 기존 토큰 삭제 후 새 토큰 발급
        refreshTokenRepository.delete(stored);
        redisService.delete("auth:refresh:" + memberId);

        String newAccessToken  = jwtTokenProvider.createAccessToken(
                member.getId(), member.getProjectId(), member.getRoleType());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtProperties.getRefreshTokenExpiryMs() / 1000);
        refreshTokenRepository.save(RefreshToken.builder()
                .memberId(memberId)
                .token(newRefreshToken)
                .expiresAt(expiresAt)
                .build());
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
