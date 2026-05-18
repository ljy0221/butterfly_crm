package com.b101.butterfly.event.dto.response;

import java.time.LocalDateTime;

public record EventCategoryStatsRow(
        Long categoryId,
        Long eventCount,
        LocalDateTime lastCollectedAt
) {
}
