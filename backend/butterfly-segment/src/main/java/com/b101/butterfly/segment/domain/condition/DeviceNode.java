package com.b101.butterfly.segment.domain.condition;

import com.fasterxml.jackson.databind.JsonNode;

public record DeviceNode(String key, Operator op, JsonNode value) implements ConditionNode {
}
