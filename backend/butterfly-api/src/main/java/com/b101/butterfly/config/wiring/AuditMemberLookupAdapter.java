package com.b101.butterfly.config.wiring;

import com.b101.butterfly.auth.entity.Member;
import com.b101.butterfly.auth.repository.MemberRepository;
import com.b101.butterfly.reporting.audit.port.MemberLookupPort;
import com.b101.butterfly.reporting.audit.port.MemberLookupView;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * butterfly-api 조립 레벨에서 reporting 모듈의 SPI({@link MemberLookupPort})를 auth 모듈의
 * {@link MemberRepository}로 충족시키는 어댑터.
 *
 * <p>본 클래스를 auth 모듈에 두지 않고 api 모듈에 둔 이유:
 * <ul>
 *   <li>reporting → auth 의존이 이미 존재하므로 auth → reporting 의존을 추가하면 순환이 발생</li>
 *   <li>api는 "조립(wiring)" 모듈로 두 도메인 모두 의존 가능</li>
 *   <li>{@link com.b101.butterfly.config.wiring.CampaignExecutionPortAdapter} 와 동일한 패턴</li>
 * </ul>
 *
 * <p>{@code MemberRepository#findAllById}는 {@code @SQLRestriction("is_deleted = false")}에 의해
 * soft-deleted 멤버를 자동 제외한다 — 감사 로그는 행위 시점의 memberId만 영구 보존(append-only,
 * ADR-0008)되고, 표시되는 이름/이메일은 "현재 활성 상태인 멤버"만 노출된다.
 */
@Component
@RequiredArgsConstructor
public class AuditMemberLookupAdapter implements MemberLookupPort {

    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberLookupView> find(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return memberRepository.findById(memberId).map(AuditMemberLookupAdapter::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, MemberLookupView> findAll(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        List<Member> found = memberRepository.findAllById(memberIds);
        return found.stream().collect(Collectors.toMap(Member::getId, AuditMemberLookupAdapter::toView));
    }

    private static MemberLookupView toView(Member m) {
        return new MemberLookupView(m.getId(), m.getName(), m.getEmail());
    }
}
