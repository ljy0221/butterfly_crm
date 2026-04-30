package com.b101.butterfly.notification.dto.response;

import java.util.List;

public record FcmSendResponse(
        int successCount,
        int failureCount,
        List<FcmTokenResult> results
) {}
