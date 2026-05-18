package com.b101.butterfly.segment.port;

import java.util.Map;

public record SegmentAudienceTarget(
        Long userId,
        Map<String, String> variables
) {

    public SegmentAudienceTarget {
        variables = variables != null ? Map.copyOf(variables) : Map.of();
    }
}
