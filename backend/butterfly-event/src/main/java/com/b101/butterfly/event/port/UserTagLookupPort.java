package com.b101.butterfly.event.port;

import java.util.List;
import java.util.Map;

/**
 * 사용자별 개인화 변수 lookup port — worker fan-out 시 DeliveryTarget 의 variables map 을
 * 채우는 데 사용된다.
 *
 * <p>출처 테이블: {@code user_tags(project_id, user_id, tag_key, tag_value)}.
 *
 * <p>대량 조회 최적화 — 한 segment 의 모든 userIds 를 한 번의 호출로 묶어 N+1 회피.
 */
public interface UserTagLookupPort {

    /**
     * 여러 userId 의 모든 tag 를 일괄 조회.
     *
     * @param projectId 프로젝트
     * @param userIds   조회 대상 (빈 리스트면 빈 Map 반환)
     * @return userId → (tagKey → tagValue) 매핑. tag 가 하나도 없는 userId 는 결과 map 에 키 자체 없음.
     */
    Map<Long, Map<String, String>> getTagsByUserIds(Long projectId, List<Long> userIds);
}
