package com.b101.butterfly.segment.domain.condition;

public sealed interface ConditionNode
        permits GroupNode, EventNode, TagNode, UserNode, DeviceNode {
}
