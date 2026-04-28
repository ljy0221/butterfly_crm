package com.b101.butterfly.segment.domain.condition;

import java.util.List;

public record GroupNode(LogicOp logic, List<ConditionNode> conditions) implements ConditionNode {
}
