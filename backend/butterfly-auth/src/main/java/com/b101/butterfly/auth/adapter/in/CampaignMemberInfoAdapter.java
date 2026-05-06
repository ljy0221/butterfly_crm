package com.b101.butterfly.auth.adapter.in;

import com.b101.butterfly.auth.entity.Member;
import com.b101.butterfly.auth.repository.MemberRepository;
import com.b101.butterfly.campaign.messagetemplate.port.MemberInformationPort;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignMemberInfoAdapter implements MemberInformationPort {

    private final MemberRepository memberRepository;

    @Override
    public Map<Long, String> getMemberNames(List<Long> memberIds) {
        return memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(
                        Member::getId,
                        Member::getName
                ));
    }

    @Override
    public String getMemberName(Long memberId) {
        return memberRepository.findNameById(memberId);
    }
}
