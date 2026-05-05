package com.b101.butterfly.segment.port;

import java.util.Collection;
import java.util.Map;

/**
 * 대상 유저 목록 API용 user 요약 정보 일괄 조회 outbound port. event 모듈이 진짜 어댑터 등록.
 *
 * <p>Codex 합의 안전망:
 * <ul>
 *   <li>{@code projectId}를 같이 받아 잘못된 타 프로젝트 user_id가 segment_members에 들어와도
 *       응답에서 차단 (project 경계 강제)</li>
 *   <li>v1 known-user only 정책으로 ADR-0012 anonymous placeholder({@code __anonymous__}) user는
 *       어댑터에서 제외 — 응답에 노출되지 않도록</li>
 * </ul>
 *
 * <p>미합류 환경에선 {@link com.b101.butterfly.segment.port.stub.StubUserSummaryReader}가
 * 빈 맵을 반환 — userId만 노출되고 다른 필드는 null.
 */
public interface UserSummaryReader {

    /**
     * 주어진 userId 리스트의 user 요약 정보를 일괄 조회.
     *
     * @param projectId 프로젝트 경계 — 다른 프로젝트의 user_id는 결과에서 제외된다
     * @param userIds 조회할 userId 컬렉션 (비어있으면 빈 맵 반환)
     * @return userId → UserSummary. 미존재/타프로젝트/placeholder는 맵에 없음
     */
    Map<Long, UserSummary> findByUserIds(Long projectId, Collection<Long> userIds);
}
