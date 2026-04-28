package com.b101.butterfly.segment.domain.condition.validation;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import org.springframework.stereotype.Component;

/**
 * EventNode의 period 필드 정합성 검사. 위반 시 SEGMENT_400_INVALID_CONDITION_TREE.
 *
 * <p>규칙:
 * <ul>
 *   <li>{@code Relative.days > 0}
 *   <li>{@code Absolute.startDate / endDate} 모두 존재 + {@code startDate <= endDate}
 *   <li>{@code Cumulative}: 추가 검증 없음
 * </ul>
 */
@Component
public class PeriodValidator {

    public void validate(Period period) {
        switch (period) {
            case Period.Relative r -> validateRelative(r);
            case Period.Absolute a -> validateAbsolute(a);
            case Period.Cumulative ignored -> {
            }
        }
    }

    private void validateRelative(Period.Relative relative) {
        if (relative.days() <= 0) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "Period.relative.days must be positive");
        }
    }

    private void validateAbsolute(Period.Absolute absolute) {
        if (absolute.startDate() == null || absolute.endDate() == null) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "Period.absolute requires both startDate and endDate");
        }
        if (absolute.startDate().isAfter(absolute.endDate())) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "Period.absolute.startDate must be on or before endDate");
        }
    }
}
