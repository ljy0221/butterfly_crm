package com.b101.butterfly.notification.dto.request;

import java.util.List;

public record FcmSendRequest(
        String credentialJson,
        List<String> tokens,
        String title,
        String body,
        String imageUrl
) {}
