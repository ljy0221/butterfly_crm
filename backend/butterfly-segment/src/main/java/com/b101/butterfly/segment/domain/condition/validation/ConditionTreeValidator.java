package com.b101.butterfly.segment.domain.condition.validation;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.DeviceNode;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.UserNode;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import org.springframework.stereotype.Component;

/**
 * ConditionTree(루트는 항상 GroupNode) 전체 검증의 진입점.
 *
 * <p>5개 sub-validator를 트리 순회하며 호출한다. 첫 위반에서 BusinessException으로 실패.
 * depth는 소프트 가드라 BusinessException을 던지지 않고 logger.warn으로만 기록한다.
 */
@Component
public class ConditionTreeValidator {

    private final StructureValidator structureValidator;
    private final TypeFieldValidator typeFieldValidator;
    private final OperatorCompatibilityValidator operatorCompatibilityValidator;
    private final PeriodValidator periodValidator;
    private final DepthGuard depthGuard;

    public ConditionTreeValidator(
            StructureValidator structureValidator,
            TypeFieldValidator typeFieldValidator,
            OperatorCompatibilityValidator operatorCompatibilityValidator,
            PeriodValidator periodValidator,
            DepthGuard depthGuard) {
        this.structureValidator = structureValidator;
        this.typeFieldValidator = typeFieldValidator;
        this.operatorCompatibilityValidator = operatorCompatibilityValidator;
        this.periodValidator = periodValidator;
        this.depthGuard = depthGuard;
    }

    public void validate(GroupNode root) {
        walk(root, 1);
    }

    private void walk(ConditionNode node, int depth) {
        depthGuard.check(depth);
        typeFieldValidator.validate(node);

        switch (node) {
            case GroupNode g -> {
                structureValidator.validate(g);
                for (ConditionNode child : g.conditions()) {
                    walk(child, depth + 1);
                }
            }
            case EventNode e -> validateEvent(e);
            case TagNode t -> operatorCompatibilityValidator.validate(t);
            case UserNode u -> operatorCompatibilityValidator.validate(u);
            case DeviceNode d -> operatorCompatibilityValidator.validate(d);
        }
    }

    private void validateEvent(EventNode event) {
        periodValidator.validate(event.period());
        // v1: filters 미지원 — 명시적 reject (ignore 시 사용자가 적용된 줄 알고 결과 받을 위험)
        if (event.filters() != null && !event.filters().isEmpty()) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_400_EVENT_FILTER_NOT_SUPPORTED_V1);
        }
    }
}
