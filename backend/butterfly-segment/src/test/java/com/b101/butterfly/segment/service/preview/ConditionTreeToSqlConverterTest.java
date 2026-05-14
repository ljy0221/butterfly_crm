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
import com.b101.butterfly.segment.port.EventQueryContext;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionTreeToSqlConverterTest {

    private static final JsonNodeFactory NODE = JsonNodeFactory.instance;
    private static final EventQueryContext EVENT_CONTEXT =
            new EventQueryContext(1L, LocalDate.of(2026, 5, 6), ZoneId.of("Asia/Seoul"));
    private final ConditionTreeToSqlConverter converter =
            new ConditionTreeToSqlConverter(
                    new com.b101.butterfly.segment.service.preview.dialect.MysqlSqlDialect());

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
                .contains("ut.project_id = :projectId")
                .contains("ut.tag_key = :tagKey0")
                .contains("ut.tag_value = :tagVal0");
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

        assertThat(fragment.sql()).contains("ut.tag_value IN (:tagVal0)");
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

        assertThat(fragment.sql()).contains("ut.tag_value LIKE :tagVal0");
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
    @DisplayName("EVENT relative 노드 → event_daily_agg EXISTS 서브쿼리")
    void eventRelativeExists() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new EventNode(
                                        42L,
                                        List.of(),
                                        new Period.Relative(30),
                                        3)));

        SqlFragment fragment = converter.convert(tree, EVENT_CONTEXT);

        assertThat(fragment.sql())
                .contains("EXISTS (SELECT 1 FROM event_daily_agg e")
                .contains("e.user_id = u.user_id")
                .contains("e.project_id = :projectId")
                .contains("e.category_id = :eventCategoryId0")
                .contains("e.log_date >= :eventFromDate0")
                .contains("GROUP BY e.user_id")
                .contains("HAVING SUM(e.event_count) >= :eventMinCount0");
        assertThat(fragment.params()).containsEntry("projectId", 1L);
        assertThat(fragment.params()).containsEntry("eventCategoryId0", 42L);
        assertThat(fragment.params()).containsEntry("eventFromDate0", LocalDate.of(2026, 4, 6));
        assertThat(fragment.params()).containsEntry("eventMinCount0", 3);
    }

    @Test
    @DisplayName("중첩 mixed OR 트리도 EVENT와 TAG를 같은 SQL 트리로 변환한다")
    void nestedMixedOrTree() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.OR,
                        List.of(
                                new TagNode("region", Operator.EQ, NODE.textNode("서울")),
                                new EventNode(
                                        42L,
                                        List.of(),
                                        new Period.Relative(7),
                                        1)));

        SqlFragment fragment = converter.convert(tree, EVENT_CONTEXT);

        assertThat(fragment.sql())
                .contains("ut.tag_key = :tagKey0")
                .contains(" OR ")
                .contains("e.category_id = :eventCategoryId1")
                .contains("e.log_date >= :eventFromDate1");
        assertThat(fragment.params()).containsEntry("eventFromDate1", LocalDate.of(2026, 4, 29));
    }

    @Test
    @DisplayName("EVENT absolute 기간은 BETWEEN으로 변환한다")
    void eventAbsolutePeriod() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new EventNode(
                                        42L,
                                        List.of(),
                                        new Period.Absolute(
                                                LocalDate.of(2026, 5, 1),
                                                LocalDate.of(2026, 5, 5)),
                                        2)));

        SqlFragment fragment = converter.convert(tree, EVENT_CONTEXT);

        assertThat(fragment.sql())
                .contains("e.log_date BETWEEN :eventStartDate0 AND :eventEndDate0");
        assertThat(fragment.params()).containsEntry("eventStartDate0", LocalDate.of(2026, 5, 1));
        assertThat(fragment.params()).containsEntry("eventEndDate0", LocalDate.of(2026, 5, 5));
    }

    @Test
    @DisplayName("EVENT cumulative 기간은 log_date 조건을 붙이지 않는다")
    void eventCumulativePeriod() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(new EventNode(42L, List.of(), new Period.Cumulative(), 1)));

        SqlFragment fragment = converter.convert(tree, EVENT_CONTEXT);

        assertThat(fragment.sql()).doesNotContain("e.log_date");
        assertThat(fragment.params()).containsEntry("eventCategoryId0", 42L);
    }

    @Test
    @DisplayName("필터가 있는 EVENT는 raw events.event_data JSON 조건으로 변환한다")
    void eventWithFiltersUsesRawEvents() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new EventNode(
                                        42L,
                                        List.of(
                                                new EventFilter(
                                                        "productId",
                                                        Operator.EQ,
                                                        NODE.textNode("SKU-1")),
                                                new EventFilter(
                                                        "price",
                                                        Operator.GTE,
                                                        NODE.numberNode(30000))),
                                        new Period.Relative(30),
                                        2)));

        SqlFragment fragment = converter.convert(tree, EVENT_CONTEXT);

        assertThat(fragment.sql())
                .contains("EXISTS (SELECT 1 FROM events ev")
                .contains("ev.user_id = u.user_id")
                .contains("ev.project_id = :projectId")
                .contains("ev.category_id = :eventCategoryId0")
                .contains("ev.occurred_at >= :eventFromDate0")
                .contains("JSON_UNQUOTE(JSON_EXTRACT(ev.event_data, :eventFilterVal0_0_path)) = :eventFilterVal0_0")
                .contains("CAST(JSON_UNQUOTE(JSON_EXTRACT(ev.event_data, :eventFilterVal0_1_path)) AS DECIMAL(30, 10)) >= :eventFilterVal0_1")
                .contains("GROUP BY ev.user_id")
                .contains("HAVING COUNT(*) >= :eventMinCount0");
        assertThat(fragment.params()).containsEntry("eventFilterVal0_0_path", "$.\"productId\"");
        assertThat(fragment.params()).containsEntry("eventFilterVal0_0", "SKU-1");
        assertThat(fragment.params()).containsEntry("eventFilterVal0_1_path", "$.\"price\"");
        assertThat(fragment.params()).containsEntry("eventFilterVal0_1", 30000);
    }

    @Test
    @DisplayName("필터가 있는 EVENT absolute 기간은 occurred_at 반개구간으로 변환한다")
    void eventWithFiltersAbsolutePeriodUsesOccurredAtRange() {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new EventNode(
                                        42L,
                                        List.of(
                                                new EventFilter(
                                                        "color",
                                                        Operator.CONTAINS,
                                                        NODE.textNode("검정"))),
                                        new Period.Absolute(
                                                LocalDate.of(2026, 5, 1),
                                                LocalDate.of(2026, 5, 5)),
                                        1)));

        SqlFragment fragment = converter.convert(tree, EVENT_CONTEXT);

        assertThat(fragment.sql())
                .contains("ev.occurred_at >= :eventStartDate0")
                .contains("ev.occurred_at < :eventEndExclusiveDate0")
                .contains("JSON_UNQUOTE(JSON_EXTRACT(ev.event_data, :eventFilterVal0_0_path)) LIKE :eventFilterVal0_0");
        assertThat(fragment.params()).containsEntry("eventStartDate0", LocalDate.of(2026, 5, 1));
        assertThat(fragment.params())
                .containsEntry("eventEndExclusiveDate0", LocalDate.of(2026, 5, 6));
        assertThat(fragment.params()).containsEntry("eventFilterVal0_0", "%검정%");
    }

    // ---------- S14P31B101-269: OR-of-filtered-EVENT-same-bucket single scan ----------

    @Test
    @DisplayName("OR-of-filtered-EVENT-same-bucket → outer users 없이 events 한 번 scan + SUM(CASE) OR HAVING")
    void mergedFilteredEventOrSameBucket() {
        EventNode leaf1 =
                new EventNode(
                        42L,
                        List.of(new EventFilter("plan", Operator.EQ, NODE.textNode("premium"))),
                        new Period.Cumulative(),
                        30);
        EventNode leaf2 =
                new EventNode(
                        42L,
                        List.of(
                                new EventFilter(
                                        "productId", Operator.EQ, NODE.textNode("SKU-007"))),
                        new Period.Cumulative(),
                        15);
        GroupNode tree = new GroupNode(LogicOp.OR, List.of(leaf1, leaf2));

        SqlFragment fragment = converter.convert(tree, EVENT_CONTEXT);

        // outer users 안 거침 — events 한 번 scan 형태.
        assertThat(fragment.sql())
                .startsWith("SELECT ev.user_id FROM events ev")
                .contains("ev.project_id = :projectId")
                .contains("ev.category_id = :eventCategoryId0")
                .contains("GROUP BY ev.user_id")
                .contains("SUM(CASE WHEN")
                .contains(">= :eventMinCount0")
                .contains(">= :eventMinCount1")
                .contains(" OR ");
        // outer users 없는지 명시 확인.
        assertThat(fragment.sql()).doesNotContain("FROM users u");
        assertThat(fragment.params())
                .containsEntry("projectId", 1L)
                .containsEntry("eventCategoryId0", 42L)
                .containsEntry("eventMinCount0", 30)
                .containsEntry("eventMinCount1", 15)
                .containsEntry("eventFilterVal0_0", "premium")
                .containsEntry("eventFilterVal1_0", "SKU-007");
    }

    @Test
    @DisplayName("OR + 다른 categoryId — single scan 비대상 → 기존 EXISTS 경로")
    void filteredEventOrDifferentCategoryFallsBack() {
        EventNode leaf1 =
                new EventNode(
                        42L,
                        List.of(new EventFilter("plan", Operator.EQ, NODE.textNode("premium"))),
                        new Period.Cumulative(),
                        30);
        EventNode leaf2 =
                new EventNode(
                        99L,
                        List.of(
                                new EventFilter(
                                        "productId", Operator.EQ, NODE.textNode("SKU-007"))),
                        new Period.Cumulative(),
                        15);
        GroupNode tree = new GroupNode(LogicOp.OR, List.of(leaf1, leaf2));

        SqlFragment fragment = converter.convert(tree, EVENT_CONTEXT);

        // 다른 category — 기존 outer users + EXISTS 모델 유지.
        assertThat(fragment.sql())
                .startsWith("SELECT u.user_id FROM users u")
                .contains("EXISTS (SELECT 1 FROM events ev");
    }

    @Test
    @DisplayName("AND-of-filtered-EVENT — single scan 비대상 (logic != OR) → 기존 EXISTS 경로")
    void filteredEventAndFallsBack() {
        EventNode leaf1 =
                new EventNode(
                        42L,
                        List.of(new EventFilter("plan", Operator.EQ, NODE.textNode("premium"))),
                        new Period.Cumulative(),
                        30);
        EventNode leaf2 =
                new EventNode(
                        42L,
                        List.of(
                                new EventFilter(
                                        "productId", Operator.EQ, NODE.textNode("SKU-007"))),
                        new Period.Cumulative(),
                        15);
        GroupNode tree = new GroupNode(LogicOp.AND, List.of(leaf1, leaf2));

        SqlFragment fragment = converter.convert(tree, EVENT_CONTEXT);

        assertThat(fragment.sql())
                .startsWith("SELECT u.user_id FROM users u")
                .contains("EXISTS (SELECT 1 FROM events ev");
    }

    @Test
    @DisplayName("OR + filterless EVENT 포함 — single scan 비대상 → 기존 EXISTS 경로")
    void filteredEventOrWithFilterlessLeafFallsBack() {
        EventNode filtered =
                new EventNode(
                        42L,
                        List.of(new EventFilter("plan", Operator.EQ, NODE.textNode("premium"))),
                        new Period.Cumulative(),
                        30);
        EventNode filterless =
                new EventNode(42L, List.of(), new Period.Cumulative(), 15);
        GroupNode tree = new GroupNode(LogicOp.OR, List.of(filtered, filterless));

        SqlFragment fragment = converter.convert(tree, EVENT_CONTEXT);

        assertThat(fragment.sql()).startsWith("SELECT u.user_id FROM users u");
    }
}
