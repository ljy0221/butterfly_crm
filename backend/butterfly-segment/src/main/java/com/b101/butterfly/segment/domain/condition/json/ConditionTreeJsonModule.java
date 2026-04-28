package com.b101.butterfly.segment.domain.condition.json;

import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class ConditionTreeJsonModule extends SimpleModule {

    public ConditionTreeJsonModule() {
        super("ConditionTreeJsonModule");
        addDeserializer(ConditionNode.class, new ConditionNodeDeserializer());
        addSerializer(ConditionNode.class, new ConditionNodeSerializer());
    }
}
