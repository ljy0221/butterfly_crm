package com.b101.butterfly.segment.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GroupNode 트리 검증을 트리거하는 Bean Validation 어노테이션.
 * 실패 시 SEGMENT_400_INVALID_CONDITION_TREE로 매핑된다 (SegmentValidationExceptionHandler).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ConditionTreeConstraintValidator.class)
public @interface ValidConditionTree {

    String message() default "condition_tree 스키마가 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
