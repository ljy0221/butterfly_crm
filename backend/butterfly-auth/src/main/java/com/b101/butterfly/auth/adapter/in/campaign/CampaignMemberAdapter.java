package com.b101.butterfly.auth.adapter.in.campaign;

import com.b101.butterfly.auth.repository.MemberRepository;
import com.b101.butterfly.campaign.messagetemplate.port.MemberExistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignMemberAdapter implements MemberExistencePort {

    private final MemberRepository memberRepository;

    @Override
    public boolean existsMember(Long memberId) {
        return memberRepository.existsById(memberId);
    }
}
