package com.b101.butterfly.event.repository;

import java.time.LocalDateTime;

public interface UserActivitySummaryRow {
    Long getUserId();
    Long getTotalEvents();
    LocalDateTime getLastSeenAt();
    Long getPurchaseCount();
}
