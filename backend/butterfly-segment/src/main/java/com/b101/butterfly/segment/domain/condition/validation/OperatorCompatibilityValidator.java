package com.b101.butterfly.segment.domain.condition.validation;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.DeviceNode;
import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.UserNode;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * 연산자(op)와 value JSON 타입의 호환성 검사. 위반 시 SEGMENT_400_INVALID_CONDITION_TREE.
 *
 * <p>규칙:
 * <ul>
 *   <li>{@code in}: value는 비어있지 않은 array
 *   <li>{@code contains}: value는 string
 *   <li>{@code gt/lt/gte/lte}: value는 number
 *   <li>{@code eq/neq}: value는 primitive(string/number/boolean)만 허용
 * </ul>
 */
@Component
public class OperatorCompatibilityValidator {

    public void validate(TagNode node) {
        validate(node.op(), node.value());
    }

    public void validate(UserNode node) {
        validate(node.op(), node.value());
    }

    public void validate(DeviceNode node) {
        validate(node.op(), node.value());
    }

    public void validate(EventFilter filter) {
        validate(filter.op(), filter.value());
    }

    private void validate(Operator op, JsonNode value) {
        if (op == null || value == null || value.isNull()) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "Operator/value cannot be null");
        }
        switch (op) {
            case IN -> requireNonEmptyArray(value);
            case CONTAINS -> requireText(value);
            case GT, LT, GTE, LTE -> requireNumber(value);
            case EQ, NEQ -> requirePrimitive(value);
        }
    }

    private void requireNonEmptyArray(JsonNode value) {
        if (!value.isArray() || value.isEmpty()) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "Operator 'in' requires a non-empty array value");
        }
    }

    private void requireText(JsonNode value) {
        if (!value.isTextual()) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "Operator 'contains' requires a string value");
        }
    }

    private void requireNumber(JsonNode value) {
        if (!value.isNumber()) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "Operators gt/lt/gte/lte require a number value");
        }
    }

    private void requirePrimitive(JsonNode value) {
        if (!(value.isTextual() || value.isNumber() || value.isBoolean())) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "Operators eq/neq require a primitive value (string/number/boolean)");
        }
    }
}
