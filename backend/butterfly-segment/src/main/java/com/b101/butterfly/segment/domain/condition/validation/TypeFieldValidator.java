package com.b101.butterfly.segment.domain.condition.validation;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.DeviceNode;
import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.UserNode;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * 노드 타입별 필수 필드 누락을 검사한다. 위반 시 SEGMENT_400_INVALID_CONDITION_TREE.
 */
@Component
public class TypeFieldValidator {

    public void validate(ConditionNode node) {
        switch (node) {
            case GroupNode g -> validateGroup(g);
            case EventNode e -> validateEvent(e);
            case TagNode t -> validateLeaf(t.key(), t.op(), t.value());
            case UserNode u -> validateLeaf(u.key(), u.op(), u.value());
            case DeviceNode d -> validateLeaf(d.key(), d.op(), d.value());
        }
    }

    private void validateGroup(GroupNode group) {
        if (group.logic() == null) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE, "GroupNode missing logic");
        }
    }

    private void validateEvent(EventNode event) {
        if (event.categoryId() == null || event.categoryId() <= 0) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "EventNode categoryId must be > 0");
        }
        if (event.period() == null) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE, "EventNode missing period");
        }
        if (event.minCount() == null || event.minCount() < 1) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "EventNode minCount must be >= 1");
        }
    }

    public void validate(EventFilter filter) {
        if (filter == null) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "EventFilter cannot be null");
        }
        validateLeaf(filter.key(), filter.op(), filter.value());
    }

    private void validateLeaf(String key, Operator op, JsonNode value) {
        if (isBlank(key)) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE, "Leaf node missing key");
        }
        if (op == null) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE, "Leaf node missing op");
        }
        if (value == null || value.isNull()) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE, "Leaf node missing value");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
