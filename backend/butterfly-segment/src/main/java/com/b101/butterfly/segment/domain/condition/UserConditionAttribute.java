package com.b101.butterfly.segment.domain.condition;

import java.util.List;

/**
 * 세그먼트 USER 조건에서 사용할 수 있는 사용자 속성 메타데이터.
 */
public record UserConditionAttribute(
        String key,
        String column,
        String label,
        String valueType,
        List<Operator> operators) {}
