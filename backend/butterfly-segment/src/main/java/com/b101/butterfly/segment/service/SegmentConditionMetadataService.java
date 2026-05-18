package com.b101.butterfly.segment.service;

import com.b101.butterfly.segment.domain.condition.UserConditionAttribute;
import com.b101.butterfly.segment.domain.condition.UserConditionAttributes;
import com.b101.butterfly.segment.dto.response.SegmentConditionMetadataResponse;
import org.springframework.stereotype.Service;

@Service
public class SegmentConditionMetadataService {

    public SegmentConditionMetadataResponse getMetadata() {
        return new SegmentConditionMetadataResponse(
                UserConditionAttributes.list().stream()
                        .map(this::toResponse)
                        .toList());
    }

    private SegmentConditionMetadataResponse.UserAttributeResponse toResponse(
            UserConditionAttribute attribute) {
        return new SegmentConditionMetadataResponse.UserAttributeResponse(
                attribute.key(),
                attribute.label(),
                attribute.valueType(),
                attribute.operators().stream().map(Enum::name).map(String::toLowerCase).toList());
    }
}
