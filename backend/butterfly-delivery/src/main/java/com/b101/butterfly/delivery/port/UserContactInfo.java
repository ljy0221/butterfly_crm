package com.b101.butterfly.delivery.port;

public record UserContactInfo(
        Long userId,
        Long deviceId,
        String pushToken,
        String email,
        String phoneNumber
) {
}
