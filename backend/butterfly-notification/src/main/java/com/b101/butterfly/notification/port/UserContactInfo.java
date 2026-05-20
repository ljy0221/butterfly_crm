package com.b101.butterfly.notification.port;

public record UserContactInfo(
        Long userId,
        Long deviceId,
        String pushToken,
        String email,
        String phoneNumber,
        String birthDate,
        String signedUpAt
) {
}
