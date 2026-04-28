package com.b101.butterfly.segment.domain.condition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum LogicOp {

    AND("and"),
    OR("or");

    private final String json;

    LogicOp(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static LogicOp fromJson(String value) {
        return Arrays.stream(values())
                .filter(op -> op.json.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown logic op: " + value));
    }
}
