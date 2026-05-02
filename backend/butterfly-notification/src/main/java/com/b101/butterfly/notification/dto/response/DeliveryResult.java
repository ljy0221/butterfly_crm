package com.b101.butterfly.notification.dto.response;

public record DeliveryResult(
        boolean successful,
        String errorCode,
        String errorMessage,
        boolean retryable
) {

    public static DeliveryResult success() {
        return new DeliveryResult(true, null, null, false);
    }

    public static DeliveryResult permanentFailure(String errorCode, String errorMessage) {
        return new DeliveryResult(false, errorCode, errorMessage, false);
    }

    public static DeliveryResult transientFailure(String errorCode, String errorMessage) {
        return new DeliveryResult(false, errorCode, errorMessage, true);
    }
}
