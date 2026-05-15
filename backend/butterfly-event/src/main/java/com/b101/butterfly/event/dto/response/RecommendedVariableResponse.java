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
     * @param key       tag_key (변수명) — 사용자가 클릭하면 {@code {{key}}} 가 본문에 삽입됨
     * @param userCount 해당 key 를 보유한 사용자 수 (정렬/시각 빈도 표시)
     */
    public record Item(String key, long userCount) {}
}
