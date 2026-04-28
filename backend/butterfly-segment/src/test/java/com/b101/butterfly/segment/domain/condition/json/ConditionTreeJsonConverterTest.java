package com.b101.butterfly.segment.domain.condition.json;

import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionTreeJsonConverterTest {

    private final ConditionTreeJsonConverter converter = new ConditionTreeJsonConverter();

    @Test
    @DisplayName("null GroupNode를 직렬화하면 null이 반환된다")
    void serializeNullReturnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("null/blank 문자열을 역직렬화하면 null이 반환된다")
    void deserializeNullOrBlankReturnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("")).isNull();
        assertThat(converter.convertToEntityAttribute("   ")).isNull();
    }

    @Test
    @DisplayName("올바른 JSON은 정상적으로 GroupNode로 역직렬화된다")
    void deserializeValidJson() {
        String json =
                "{\"logic\":\"and\",\"conditions\":[{\"type\":\"tag\","
                        + "\"key\":\"region\",\"op\":\"eq\",\"value\":\"서울\"}]}";

        GroupNode result = converter.convertToEntityAttribute(json);

        assertThat(result).isNotNull();
        assertThat(result.logic()).isEqualTo(LogicOp.AND);
        assertThat(result.conditions()).hasSize(1);
        assertThat(result.conditions().get(0)).isInstanceOf(TagNode.class);
    }

    @Test
    @DisplayName("객체에서 JSON 문자열로 round-trip이 가능하다")
    void roundTripFromObject() {
        ConditionNode tag =
                new TagNode("grade", Operator.IN, JsonNodeFactory.instance.arrayNode().add("VIP"));
        GroupNode original = new GroupNode(LogicOp.OR, List.of(tag));

        String json = converter.convertToDatabaseColumn(original);
        GroupNode restored = converter.convertToEntityAttribute(json);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("잘못된 JSON 문자열은 IllegalStateException으로 래핑되어 던져진다")
    void invalidJsonWrapsAsIllegalState() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-a-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deserialize condition tree");
    }

    @Test
    @DisplayName("미지원 type 값은 IllegalStateException으로 래핑되어 던져진다")
    void unknownNodeTypeWraps() {
        String json =
                "{\"logic\":\"and\",\"conditions\":[{\"type\":\"unknown\","
                        + "\"key\":\"x\",\"op\":\"eq\",\"value\":\"y\"}]}";

        assertThatThrownBy(() -> converter.convertToEntityAttribute(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deserialize condition tree");
    }
}
