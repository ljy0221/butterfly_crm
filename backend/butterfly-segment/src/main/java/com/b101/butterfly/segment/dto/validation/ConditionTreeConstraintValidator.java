package com.b101.butterfly.segment.dto.validation;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.validation.ConditionTreeValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * @ValidConditionTree → Phase 1 ConditionTreeValidator 위임. 실패 시 false 리턴(단일 코드 매핑).
 *
 * <p>Phase 3 단계는 단일 에러 코드로 단순화. 세부 코드 구분(EMPTY_CONDITIONS / INVALID_CONDITION_TREE)은
 * Phase 4 service가 직접 ConditionTreeValidator를 호출해 BusinessException으로 던지는 경로에서만 사용.
 */
public class ConditionTreeConstraintValidator
        implements ConstraintValidator<ValidConditionTree, GroupNode> {

    private final ConditionTreeValidator conditionTreeValidator;

    public ConditionTreeConstraintValidator(ConditionTreeValidator conditionTreeValidator) {
        this.conditionTreeValidator = conditionTreeValidator;
    }

    @Override
    public boolean isValid(GroupNode value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            conditionTreeValidator.validate(value);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }
}
