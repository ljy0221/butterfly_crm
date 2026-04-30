package com.b101.butterfly.notification.dto.response;

public record CallbackResponse(
        boolean ok
) {

    public static CallbackResponse accepted() {
        return new CallbackResponse(true);
    }
}
