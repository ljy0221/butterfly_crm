package com.b101.butterfly.event.dto.response;

import java.util.List;

/**
 * 메시지 템플릿 편집 시 본문에 삽입할 수 있는 변수 추천 목록.
 *
 * <p>출처: 프로젝트(또는 옵션 세그먼트) 에서 실제 보유 중인 {@code user_tags.tag_key} 의
 * distinct 집계. 클라이언트가 클릭 → {@code {{tagKey}}} 본문 삽입에 사용.
 */
public record RecommendedVariableResponse(List<Item> variables) {

    /**
     * @param key            변수명 — 사용자가 클릭하면 {@code {{key}}} 가 본문에 삽입됨
     * @param userCount      기존 프론트 호환용 보유 사용자 수. 신규 응답에서는 availableCount 와 동일 의미.
     * @param source         변수 출처. 예: USER_TAG, COUPON, CAMPAIGN, SYSTEM, EVENT_CONTEXT.
     * @param status         선택 세그먼트 기준 사용 가능 상태. 예: READY, PARTIAL, MISSING, SYSTEM_PROVIDED.
     * @param availableCount 값을 보유한 대상자 수.
     * @param missingCount   값을 보유하지 않은 대상자 수.
     * @param insertToken    템플릿 본문에 삽입할 토큰. 예: {@code {{name}}}.
     */
    public record Item(
            String key,
            long userCount,
            String source,
            String status,
            long availableCount,
            long missingCount,
            String insertToken
    ) {

        public Item(String key, long userCount) {
            this(key, userCount, "USER_TAG", "READY", userCount, 0, "{{" + key + "}}");
        }
    }
}
