package com.b101.butterfly.segment.domain.condition.validation;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionTreeValidatorOperatorTest {

    private static final JsonNodeFactory NODE = JsonNodeFactory.instance;

    private final OperatorCompatibilityValidator validator = new OperatorCompatibilityValidator();

    @Test
    @DisplayName("IN: 비어있지 않은 array 허용, 그 외 거절")
    void inOperator() {
        assertThatCode(() -> validator.validate(tag(Operator.IN, NODE.arrayNode().add("a").add("b"))))
                .doesNotThrowAnyException();

        assertInvalidConditionTree(() -> validator.validate(tag(Operator.IN, NODE.arrayNode())));
        assertInvalidConditionTree(() -> validator.validate(tag(Operator.IN, NODE.textNode("a"))));
        assertInvalidConditionTree(() -> validator.validate(tag(Operator.IN, NODE.numberNode(1))));
    }

    @Test
    @DisplayName("CONTAINS: string 허용, 그 외 거절")
    void containsOperator() {
        assertThatCode(() -> validator.validate(tag(Operator.CONTAINS, NODE.textNode("foo"))))
                .doesNotThrowAnyException();

        assertInvalidConditionTree(
                () -> validator.validate(tag(Operator.CONTAINS, NODE.numberNode(1))));
        assertInvalidConditionTree(
                () -> validator.validate(tag(Operator.CONTAINS, NODE.arrayNode().add("a"))));
        assertInvalidConditionTree(
                () -> validator.validate(tag(Operator.CONTAINS, NODE.booleanNode(true))));
    }

    @Test
    @DisplayName("GT/LT/GTE/LTE: number 허용, 그 외 거절")
    void numericComparators() {
        for (Operator op : new Operator[] {Operator.GT, Operator.LT, Operator.GTE, Operator.LTE}) {
            assertThatCode(() -> validator.validate(tag(op, NODE.numberNode(42))))
                    .as("%s with number", op)
                    .doesNotThrowAnyException();

            assertInvalidConditionTree(() -> validator.validate(tag(op, NODE.textNode("42"))));
            assertInvalidConditionTree(
                    () -> validator.validate(tag(op, NODE.arrayNode().add(42))));
            assertInvalidConditionTree(() -> validator.validate(tag(op, NODE.booleanNode(true))));
        }
    }

    @Test
    @DisplayName("EQ/NEQ: primitive(string/number/boolean) 허용, array/object 거절")
    void equalityOperators() {
        for (Operator op : new Operator[] {Operator.EQ, Operator.NEQ}) {
            assertThatCode(() -> validator.validate(tag(op, NODE.textNode("x"))))
                    .as("%s with string", op)
                    .doesNotThrowAnyException();
            assertThatCode(() -> validator.validate(tag(op, NODE.numberNode(1))))
                    .as("%s with number", op)
                    .doesNotThrowAnyException();
            assertThatCode(() -> validator.validate(tag(op, NODE.booleanNode(false))))
                    .as("%s with boolean", op)
                    .doesNotThrowAnyException();

            assertInvalidConditionTree(
                    () -> validator.validate(tag(op, NODE.arrayNode().add("x"))));
            assertInvalidConditionTree(
                    () -> validator.validate(tag(op, NODE.objectNode().put("k", "v"))));
        }
    }

    @Test
    @DisplayName("op 또는 value가 null이면 거절")
    void rejectNullOpOrValue() {
        assertInvalidConditionTree(() -> validator.validate(tag(null, NODE.textNode("x"))));
        assertInvalidConditionTree(() -> validator.validate(tag(Operator.EQ, null)));
    }

    @Test
    @DisplayName("EventFilter도 동일 규칙으로 검증된다")
    void eventFilterShareRules() {
        assertThatCode(
                        () ->
                                validator.validate(
                                        new EventFilter(
                                                "color", Operator.EQ, NODE.textNode("red"))))
                .doesNotThrowAnyException();

        assertInvalidConditionTree(
                () ->
                        validator.validate(
                                new EventFilter("color", Operator.IN, NODE.textNode("red"))));
    }

    private static TagNode tag(Operator op, JsonNode value) {
        return new TagNode("k", op, value);
    }

    private static void assertInvalidConditionTree(ThrowingRunnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Throwable;
    }
}
