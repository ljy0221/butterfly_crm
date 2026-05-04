package com.b101.butterfly.segment.service.preview;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.DeviceNode;
import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.UserNode;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionTreeToSqlConverterTest {

    private static final JsonNodeFactory NODE = JsonNodeFactory.instance;
    private final ConditionTreeToSqlConverter converter = new ConditionTreeToSqlConverter();

    @Test
    @DisplayName("단일 TAG eq → user_tags EXISTS 서브쿼리")
    void singleTagEq() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(new TagNode("region", Operator.EQ, NODE.textNode("서울"))));

        SqlFragment fragment = converter.convert(tree, 1L);

        assertThat(fragment.sql())
                .startsWith("SELECT u.user_id FROM users u WHERE u.project_id = :projectId")
                .contains("EXISTS (SELECT 1 FROM user_tags ut")
                .contains("ut.`key` = :tagKey0")
                .contains("ut.value = :tagVal0");
        assertThat(fragment.params()).containsEntry("projectId", 1L);
        assertThat(fragment.params()).containsEntry("tagKey0", "region");
        assertThat(fragment.params()).containsEntry("tagVal0", "서울");
    }

    @Test
    @DisplayName("USER 컬럼은 화이트리스트로 매핑된다 (birthDate → birth_date)")
    void userColumnWhitelist() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(new UserNode("birthDate", Operator.LT, NODE.textNode("2000-01-01"))));

        SqlFragment fragment = converter.convert(tree, 1L);

        assertThat(fragment.sql()).contains("u.birth_date < :userVal0");
        assertThat(fragment.params()).containsEntry("userVal0", "2000-01-01");
    }

    @Test
    @DisplayName("USER 키가 화이트리스트 밖이면 SEGMENT_400_INVALID_CONDITION_TREE")
    void userKeyNotWhitelisted() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(new UserNode("password", Operator.EQ, NODE.textNode("x"))));

        assertThatThrownBy(() -> converter.convert(tree, 1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(
                                                SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE));
    }

    @Test
    @DisplayName("DEVICE eq → devices EXISTS 서브쿼리 + 화이트리스트 매핑")
    void deviceEq() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(new DeviceNode("platform", Operator.EQ, NODE.textNode("ANDROID"))));

        SqlFragment fragment = converter.convert(tree, 1L);

        assertThat(fragment.sql())
                .contains("EXISTS (SELECT 1 FROM devices d WHERE d.user_id = u.user_id")
                .contains("d.platform = :deviceVal0");
        assertThat(fragment.params()).containsEntry("deviceVal0", "ANDROID");
    }

    @Test
    @DisplayName("op IN → 배열을 List로 바인딩")
    void inOperator() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new TagNode(
                                        "grade",
                                        Operator.IN,
                                        NODE.arrayNode().add("VIP").add("VVIP"))));

        SqlFragment fragment = converter.convert(tree, 1L);

        assertThat(fragment.sql()).contains("ut.value IN (:tagVal0)");
        assertThat(fragment.params().get("tagVal0")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> bound = (List<Object>) fragment.params().get("tagVal0");
        assertThat(bound).containsExactly("VIP", "VVIP");
    }

    @Test
    @DisplayName("op CONTAINS → LIKE + value를 %v%로 wrap")
    void containsOperator() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(new TagNode("name", Operator.CONTAINS, NODE.textNode("foo"))));

        SqlFragment fragment = converter.convert(tree, 1L);

        assertThat(fragment.sql()).contains("ut.value LIKE :tagVal0");
        assertThat(fragment.params()).containsEntry("tagVal0", "%foo%");
    }

    @Test
    @DisplayName("AND/OR 그룹은 괄호로 묶여 join된다")
    void groupLogicJoin() {
        GroupNode innerOr =
                new GroupNode(
                        LogicOp.OR,
                        List.of(
                                new TagNode("region", Operator.EQ, NODE.textNode("서울")),
                                new TagNode("region", Operator.EQ, NODE.textNode("경기"))));
        GroupNode root =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                innerOr,
                                new UserNode("pushOptIn", Operator.EQ, NODE.booleanNode(true))));

        SqlFragment fragment = converter.convert(root, 1L);

        // root: ((or-clause) AND (user-clause))
        assertThat(fragment.sql()).contains(" OR ").contains(" AND ");
        assertThat(fragment.params()).containsEntry("userVal2", true);
    }

    @Test
    @DisplayName("EVENT 노드를 만나면 EventConditionNotReadyException을 던진다")
    void eventNotReady() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new EventNode(
                                        42L,
                                        List.of(),
                                        new Period.Relative(30),
                                        3)));

        assertThatThrownBy(() -> converter.convert(tree, 1L))
                .isInstanceOf(EventConditionNotReadyException.class);
    }

    @Test
    @DisplayName("중첩 EVENT도 동일하게 EventConditionNotReadyException")
    void nestedEventNotReady() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.OR,
                        List.of(
                                new TagNode("region", Operator.EQ, NODE.textNode("서울")),
                                new EventNode(
                                        42L,
                                        List.of(
                                                new EventFilter(
                                                        "color",
                                                        Operator.EQ,
                                                        NODE.textNode("검정"))),
                                        new Period.Relative(7),
                                        1)));

        assertThatThrownBy(() -> converter.convert(tree, 1L))
                .isInstanceOf(EventConditionNotReadyException.class);
    }
}
