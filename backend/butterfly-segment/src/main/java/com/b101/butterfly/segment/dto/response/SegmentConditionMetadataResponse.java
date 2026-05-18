package com.b101.butterfly.segment.dto.response;

import java.util.List;

public record SegmentConditionMetadataResponse(List<UserAttributeResponse> userAttributes) {

    public record UserAttributeResponse(
            String key,
            String label,
            String valueType,
            List<String> operators) {}
}
