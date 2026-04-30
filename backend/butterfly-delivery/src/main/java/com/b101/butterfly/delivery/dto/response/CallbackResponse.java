package com.b101.butterfly.delivery.dto.response;

public record CallbackResponse(
        boolean ok
) {

    public static CallbackResponse accepted() {
        return new CallbackResponse(true);
    }
}
