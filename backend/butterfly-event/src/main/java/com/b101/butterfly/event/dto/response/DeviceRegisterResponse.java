package com.b101.butterfly.event.dto.response;

public record DeviceRegisterResponse(
        Long internalId,
        Boolean tokenValid
) {
}
