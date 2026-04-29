package com.b101.butterfly.segment.domain.condition.validation;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.json.ConditionTreeJsonConverter;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionTreeValidatorStructureTest {

    private final ConditionTreeValidator validator =
            new ConditionTreeValidator(
                    new StructureValidator(),
                    new TypeFieldValidator(),
                    new OperatorCompatibilityValidator(),
                    new PeriodValidator(),
                    new DepthGuard());

    @Test
    @DisplayName("빈 conditions 리스트는 SEGMENT_400_EMPTY_CONDITIONS로 거절된다")
    void rejectEmptyConditionsList() {
        GroupNode tree = new GroupNode(LogicOp.AND, List.of());

        assertThatThrownBy(() -> validator.validate(tree))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_400_EMPTY_CONDITIONS));
    }

    @Test
    @DisplayName("null conditions는 SEGMENT_400_EMPTY_CONDITIONS로 거절된다")
    void rejectNullConditions() {
        GroupNode tree = new GroupNode(LogicOp.OR, null);

        assertThatThrownBy(() -> validator.validate(tree))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_400_EMPTY_CONDITIONS));
    }

    @Test
    @DisplayName("픽스처 invalid-empty-conditions.json은 SEGMENT_400_EMPTY_CONDITIONS로 거절된다")
    void rejectFixtureEmptyConditions() throws IOException {
        ConditionTreeJsonConverter converter = new ConditionTreeJsonConverter();
        String json = readResource("condition-tree/invalid-empty-conditions.json");
        GroupNode tree = converter.convertToEntityAttribute(json);

        assertThatThrownBy(() -> validator.validate(tree))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_400_EMPTY_CONDITIONS));
    }

    @Test
    @DisplayName("최소 1개 조건이 있으면 통과한다")
    void acceptSingleConditionTree() {
        ConditionNode tag =
                new TagNode("region", Operator.EQ, JsonNodeFactory.instance.textNode("서울"));
        GroupNode tree = new GroupNode(LogicOp.AND, List.of(tag));

        assertThatCode(() -> validator.validate(tree)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("중첩 그룹의 안쪽 conditions가 비면 SEGMENT_400_EMPTY_CONDITIONS로 거절된다")
    void rejectNestedEmptyGroup() {
        GroupNode innerEmpty = new GroupNode(LogicOp.AND, List.of());
        GroupNode tree = new GroupNode(LogicOp.OR, List.of(innerEmpty));

        assertThatThrownBy(() -> validator.validate(tree))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_400_EMPTY_CONDITIONS));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in =
                Objects.requireNonNull(
                        ConditionTreeValidatorStructureTest.class
                                .getClassLoader()
                                .getResourceAsStream(path),
                        "Resource not found: " + path)) {
            return new String(in.readAllBytes());
        }
    }
}
