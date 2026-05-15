package com.b101.butterfly.ai.dto;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SegmentDraftRequest(
        @NotBlank @Size(max = 500) String prompt,
        @Valid SegmentDraftContext context,
        List<@Valid EventCategoryHint> availableEvents,
        List<@Size(max = 40) String> availableTagKeys) {

    public SegmentDraftRequest {
        prompt = trim(prompt);
        context = context == null ? new SegmentDraftContext(null, null, null) : context;
        availableEvents = availableEvents == null ? List.of() : List.copyOf(availableEvents);
        availableTagKeys = availableTagKeys == null ? List.of() : List.copyOf(availableTagKeys);
    }

    public record SegmentDraftContext(
            @Size(max = 30) String name,
            @Size(max = 100) String description,
            GroupNode currentConditionTree) {

        public SegmentDraftContext {
            name = trim(name);
            description = trim(description);
        }
    }

    public record EventCategoryHint(
            @Positive Long categoryId,
            @Size(max = 80) String categoryName,
            @Size(max = 80) String displayName,
            @Size(max = 200) String description) {

        public EventCategoryHint {
            categoryName = trim(categoryName);
            displayName = trim(displayName);
            description = trim(description);
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
