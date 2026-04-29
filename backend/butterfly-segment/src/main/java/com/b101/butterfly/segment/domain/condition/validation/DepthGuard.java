package com.b101.butterfly.segment.domain.condition.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 트리 깊이 4단 이상이면 경고만 기록하는 소프트 가드. ADR-0006 §7.
 */
@Component
public class DepthGuard {

    static final int WARN_DEPTH_THRESHOLD = 4;

    private static final Logger log = LoggerFactory.getLogger(DepthGuard.class);

    public void check(int depth) {
        if (depth >= WARN_DEPTH_THRESHOLD) {
            log.warn(
                    "ConditionTree depth {} exceeds soft guard threshold {} (ADR-0006 §7).",
                    depth,
                    WARN_DEPTH_THRESHOLD);
        }
    }
}
