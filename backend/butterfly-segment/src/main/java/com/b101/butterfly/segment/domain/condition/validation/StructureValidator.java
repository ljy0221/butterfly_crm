package com.b101.butterfly.segment.domain.condition.validation;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import org.springframework.stereotype.Component;

/**
 * 그룹 노드의 conditions 비어있음 여부를 검사한다. SEGMENT_400_EMPTY_CONDITIONS.
 */
@Component
public class StructureValidator {

    public void validate(GroupNode group) {
        if (group.conditions() == null || group.conditions().isEmpty()) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_400_EMPTY_CONDITIONS);
        }
    }
}
