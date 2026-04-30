package com.b101.butterfly.delivery.dto.response;

public record FcmTokenResult(
        String token,
        boolean successful,
        String messageId,
        String errorCode,
        String errorMessage
) {

    public static FcmTokenResult success(String token, String messageId) {
        return new FcmTokenResult(token, true, messageId, null, null);
    }

    public static FcmTokenResult failure(String token, String errorCode, String errorMessage) {
        return new FcmTokenResult(token, false, null, errorCode, errorMessage);
    }
}
