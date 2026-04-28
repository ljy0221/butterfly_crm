package com.b101.butterfly.segment.domain.condition;

import java.util.List;

public record EventNode(
        String category,
        List<EventFilter> filters,
        Period period,
        Integer minCount
) implements ConditionNode {
}
