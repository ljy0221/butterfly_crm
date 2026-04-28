package com.b101.butterfly.segment.domain.condition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Operator {

    EQ("eq"),
    NEQ("neq"),
    GT("gt"),
    LT("lt"),
    GTE("gte"),
    LTE("lte"),
    IN("in"),
    CONTAINS("contains");

    private final String json;

    Operator(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static Operator fromJson(String value) {
        return Arrays.stream(values())
                .filter(op -> op.json.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown operator: " + value));
    }
}
