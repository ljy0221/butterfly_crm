package com.b101.butterfly.notification.dto.request;

import java.time.LocalDateTime;

public record ClickedCallbackRequest(
        LocalDateTime clickedAt
) {}
