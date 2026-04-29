package com.b101.butterfly.segment.dto.request;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.dto.validation.ValidConditionTree;
import jakarta.validation.constraints.NotNull;

/**
 * 미리보기 요청 (#42). 저장 없이 conditionTree만 평가.
 */
public record PreviewRequest(@NotNull @ValidConditionTree GroupNode conditionTree) {
}
