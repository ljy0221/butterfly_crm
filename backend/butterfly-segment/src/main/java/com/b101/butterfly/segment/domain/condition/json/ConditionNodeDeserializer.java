package com.b101.butterfly.segment.domain.condition.json;

import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.DeviceNode;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.UserNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class ConditionNodeDeserializer extends JsonDeserializer<ConditionNode> {

    @Override
    public ConditionNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode tree = p.readValueAsTree();

        if (!tree.isObject()) {
            throw JsonMappingException.from(p, "ConditionNode must be a JSON object: " + tree);
        }

        if (tree.has("logic")) {
            return ctxt.readTreeAsValue(tree, GroupNode.class);
        }

        if (!tree.has("type")) {
            throw JsonMappingException.from(
                    p, "ConditionNode missing discriminator: needs 'logic' or 'type'");
        }

        String type = tree.get("type").asText();
        return switch (type) {
            case "event" -> ctxt.readTreeAsValue(tree, EventNode.class);
            case "tag" -> ctxt.readTreeAsValue(tree, TagNode.class);
            case "user" -> ctxt.readTreeAsValue(tree, UserNode.class);
            case "device" -> ctxt.readTreeAsValue(tree, DeviceNode.class);
            default -> throw JsonMappingException.from(p, "Unknown condition node type: " + type);
        };
    }
}
