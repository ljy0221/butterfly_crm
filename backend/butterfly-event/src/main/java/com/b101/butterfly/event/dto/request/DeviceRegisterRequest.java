package com.b101.butterfly.event.dto.request;

import com.b101.butterfly.event.entity.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceRegisterRequest(
        @NotBlank @Size(max = 255) String deviceId,
        @Size(max = 255) String userId,
        @NotNull Platform platform,
        @NotNull @Size(max = 500) String pushToken,
        @NotBlank @Size(max = 50) String appVersion,
        @NotBlank @Size(max = 50) String osVersion
) {
}
