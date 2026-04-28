package com.b101.butterfly.segment.domain.condition;

import com.fasterxml.jackson.databind.JsonNode;

public record EventFilter(String key, Operator op, JsonNode value) {
}
