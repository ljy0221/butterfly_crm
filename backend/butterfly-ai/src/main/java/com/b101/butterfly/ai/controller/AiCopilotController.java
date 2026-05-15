package com.b101.butterfly.ai.controller;

import com.b101.butterfly.ai.dto.CampaignDraftRequest;
import com.b101.butterfly.ai.dto.CampaignDraftResponse;
import com.b101.butterfly.ai.dto.SegmentDraftRequest;
import com.b101.butterfly.ai.dto.SegmentDraftResponse;
import com.b101.butterfly.ai.service.AiCopilotService;
import com.b101.butterfly.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("api")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/ai")
public class AiCopilotController {

    private final AiCopilotService aiCopilotService;

    @PostMapping("/campaign-draft")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<CampaignDraftResponse> generateCampaignDraft(
            @PathVariable @Positive Long projectId,
            @Valid @RequestBody CampaignDraftRequest request) {
        return ApiResponse.ok(aiCopilotService.generateCampaignDraft(projectId, request));
    }

    @PostMapping("/segment-draft")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<SegmentDraftResponse> generateSegmentDraft(
            @PathVariable @Positive Long projectId,
            @Valid @RequestBody SegmentDraftRequest request) {
        return ApiResponse.ok(aiCopilotService.generateSegmentDraft(projectId, request));
    }
}
