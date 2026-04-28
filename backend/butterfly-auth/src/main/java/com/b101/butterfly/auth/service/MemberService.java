package com.b101.butterfly.auth.service;

import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.dto.request.PasswordChangeRequest;
import com.b101.butterfly.auth.dto.request.PhoneChangeRequest;
import com.b101.butterfly.auth.dto.request.WithdrawRequest;
import com.b101.butterfly.auth.dto.response.MemberInfoResponse;
import com.b101.butterfly.auth.entity.Member;
import com.b101.butterfly.auth.exception.AuthErrorCode;
import com.b101.butterfly.auth.repository.MemberRepository;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberInfoResponse getMyInfo(Long memberId) {
        return MemberInfoResponse.from(findMember(memberId));
    }

    @Transactional
    public void changePassword(Long memberId, PasswordChangeRequest request) {
        Member member = findMember(memberId);

        if (member.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), member.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.CURRENT_PASSWORD_WRONG);
        }
        if (passwordEncoder.matches(request.newPassword(), member.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.SAME_PASSWORD);
        }

        member.updatePassword(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public void changePhone(Long memberId, PhoneChangeRequest request) {
        findMember(memberId).updatePhone(request.phone());
    }

    @Transactional
    public void withdraw(Long memberId, WithdrawRequest request) {
        Member member = findMember(memberId);

        if (member.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.CURRENT_PASSWORD_WRONG);
        }
        if (member.getRoleType() == RoleType.OWNER) {
            throw new BusinessException(AuthErrorCode.OWNER_CANNOT_WITHDRAW);
        }
        member.softDelete();
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));
    }
}
