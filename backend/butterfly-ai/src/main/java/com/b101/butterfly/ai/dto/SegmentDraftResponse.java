package com.b101.butterfly.ai.dto;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

public record SegmentDraftResponse(
        String name,
        String description,
        GroupNode conditionTree,
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> notes) {

    public SegmentDraftResponse {
        name = defaultText(name, "AI 세그먼트 초안");
        description = defaultText(description, "AI가 생성한 세그먼트 조건 초안입니다.");
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
