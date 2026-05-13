package com.b101.butterfly.notification.controller;

import com.b101.butterfly.notification.dto.request.FcmSendRequest;
import com.b101.butterfly.notification.dto.response.FcmSendResponse;
import com.b101.butterfly.notification.service.fcm.FcmAppManager;
import com.b101.butterfly.notification.service.fcm.FcmService;
import com.b101.butterfly.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("api")
@RequestMapping("/api/v1/projects/{projectId}/fcm")
@RequiredArgsConstructor
public class FcmController {

    private final FcmService fcmService;
    private final FcmAppManager fcmAppManager;

    @PostMapping("/send")
    public ApiResponse<FcmSendResponse> send(
            @PathVariable Long projectId,
            @RequestBody FcmSendRequest request) {

        return ApiResponse.ok(fcmService.sendAll(projectId, request));
    }

    @DeleteMapping("/credentials/cache")
    public ApiResponse<Void> invalidateCache(@PathVariable Long projectId) {
        fcmAppManager.invalidate(projectId);
        return ApiResponse.ok();
    }
}
