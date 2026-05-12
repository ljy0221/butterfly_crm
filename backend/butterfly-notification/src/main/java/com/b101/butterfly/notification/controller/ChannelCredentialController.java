package com.b101.butterfly.notification.controller;

import com.b101.butterfly.notification.dto.request.ChannelCredentialRequest;
import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.service.credential.ChannelCredentialService;
import com.b101.butterfly.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/credentials")
@RequiredArgsConstructor
public class ChannelCredentialController {

    private final ChannelCredentialService channelCredentialService;

    @PutMapping("/{channel}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ApiResponse<Void> saveCredential(
            @PathVariable Long projectId,
            @PathVariable DeliveryCommand.Channel channel,
            @RequestBody ChannelCredentialRequest request) {

        channelCredentialService.saveCredential(projectId, channel, request.credentialData());
        return ApiResponse.ok();
    }

    @GetMapping("/{channel}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ApiResponse<String> getCredential(
            @PathVariable Long projectId,
            @PathVariable DeliveryCommand.Channel channel) {

        return ApiResponse.ok(channelCredentialService.getCredentialData(projectId, channel));
    }

    @DeleteMapping("/{channel}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ApiResponse<Void> deleteCredential(
            @PathVariable Long projectId,
            @PathVariable DeliveryCommand.Channel channel) {

        channelCredentialService.deleteCredential(projectId, channel);
        return ApiResponse.ok();
    }
}
