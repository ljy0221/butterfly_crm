package com.b101.butterfly.reporting.audit.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 감사 로그 응답에 memberName / memberEmail 을 채우기 위한 SPI.
 *
 * <p>reporting 도메인은 본 인터페이스를 통해서만 멤버 식별 정보에 접근하고, 실제 어댑터는 auth 모듈에서
 * 제공한다. 구현 부재 시(예: 단위 테스트) 호출 측은 빈 결과를 받아야 하며 응답은 ID-only로 폴백한다.
 *
 * <p>ADR-0014 모듈 경계: reporting → auth 도메인 직접 의존 금지. Port 인터페이스만 노출.
 */
public interface MemberLookupPort {

    /**
     * 단일 멤버 조회. 미존재(혹은 soft-deleted) 시 {@link Optional#empty()}.
     */
    Optional<MemberLookupView> find(Long memberId);

    /**
     * 다건 일괄 조회 — memberId 키. 미존재 멤버는 결과 맵에 포함되지 않는다.
     *
     * <p>감사 로그 목록(API #20)에서 N+1 회피용.
     */
    Map<Long, MemberLookupView> findAll(Collection<Long> memberIds);
}
