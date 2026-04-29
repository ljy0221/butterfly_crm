package com.b101.butterfly.segment.dto.request;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.dto.validation.ValidConditionTree;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 세그먼트 생성 요청 (#37). 03-segment.md §3.
 */
public record CreateSegmentRequest(
        @NotBlank @Size(min = 1, max = 30) String name,
        @Size(max = 100) String description,
        @NotNull @ValidConditionTree GroupNode conditionTree) {
}
