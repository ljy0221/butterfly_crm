package com.b101.butterfly.segment.domain.condition.json;

import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.DeviceNode;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.UserNode;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class ConditionNodeSerializer extends JsonSerializer<ConditionNode> {

    @Override
    public void serialize(ConditionNode node, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        switch (node) {
            case GroupNode g -> writeGroup(g, gen, provider);
            case EventNode e -> writeEvent(e, gen, provider);
            case TagNode t -> writeLeaf(gen, provider, "tag", t.key(), t.op(), t.value());
            case UserNode u -> writeLeaf(gen, provider, "user", u.key(), u.op(), u.value());
            case DeviceNode d -> writeLeaf(gen, provider, "device", d.key(), d.op(), d.value());
        }
    }

    private void writeGroup(GroupNode group, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeStartObject();
        provider.defaultSerializeField("logic", group.logic(), gen);
        provider.defaultSerializeField("conditions", group.conditions(), gen);
        gen.writeEndObject();
    }

    private void writeEvent(EventNode event, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", "event");
        provider.defaultSerializeField("categoryId", event.categoryId(), gen);
        // filters는 빈 배열이라도 항상 직렬화 — 도메인 계약상 List는 항상 존재해야 하고,
        // 클라이언트(zod 등) 검증에서 non-optional 배열로 받기 때문.
        provider.defaultSerializeField(
                "filters",
                event.filters() != null ? event.filters() : java.util.List.of(),
                gen);
        provider.defaultSerializeField("period", event.period(), gen);
        provider.defaultSerializeField("minCount", event.minCount(), gen);
        gen.writeEndObject();
    }

    private void writeLeaf(
            JsonGenerator gen,
            SerializerProvider provider,
            String type,
            String key,
            Operator op,
            JsonNode value)
            throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", type);
        provider.defaultSerializeField("key", key, gen);
        provider.defaultSerializeField("op", op, gen);
        provider.defaultSerializeField("value", value, gen);
        gen.writeEndObject();
    }
}
