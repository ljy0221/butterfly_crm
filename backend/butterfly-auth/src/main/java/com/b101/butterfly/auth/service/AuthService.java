package com.b101.butterfly.auth.service;

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
import com.b101.butterfly.auth.repository.RefreshTokenRepository;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!emailVerificationService.isVerified(request.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (memberRepository.existsByEmail(request.email())) {
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

        // TODO: 인증 통합 시 JwtTokenProvider로 실제 토큰 생성
        return new LoginResponse("", "", MemberInfoResponse.from(member));
    }

    @Transactional
    public void logout(Long memberId) {
        // TODO: 인증 통합 시 accessToken jti 추출 → Redis+DB 블랙리스트 등록
        refreshTokenRepository.deleteByMemberId(memberId);
        redisService.delete("auth:refresh:" + memberId);
    }

    @Transactional
    public LoginResponse refreshToken(String refreshToken) {
        // TODO: 인증 통합 시 RTR 구현 (기존 토큰 삭제 후 새 토큰 발급)
        throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
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
