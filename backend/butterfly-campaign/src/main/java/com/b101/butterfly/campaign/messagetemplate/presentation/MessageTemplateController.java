package com.b101.butterfly.campaign.messagetemplate.presentation;

import com.b101.butterfly.campaign.messagetemplate.application.MessageTemplateService;
import com.b101.butterfly.campaign.messagetemplate.application.result.MessageTemplateDetailResult;
import com.b101.butterfly.campaign.messagetemplate.application.result.MessageTemplateListResult;
import com.b101.butterfly.campaign.messagetemplate.presentation.dto.MessageTemplateCreateRequest;
import com.b101.butterfly.campaign.messagetemplate.presentation.dto.MessageTemplateCreateResponse;
import com.b101.butterfly.campaign.messagetemplate.presentation.dto.MessageTemplateDetailResponse;
import com.b101.butterfly.campaign.messagetemplate.presentation.dto.MessageTemplateListResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/templates")
public class MessageTemplateController {

    private final MessageTemplateService messageTemplateService;

    // TODO memberId 추출 역할 검증 추가
    @PostMapping
    public ResponseEntity<MessageTemplateCreateResponse> createMessageTemplate(
            Long memberId,
            @PathVariable Long projectId,
            @RequestBody @Valid MessageTemplateCreateRequest request
    ) {
        Long messageTemplateId = messageTemplateService.createMessageTemplate(
                request.toCommand(projectId, memberId));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new MessageTemplateCreateResponse(messageTemplateId));
    }

    // TODO memberId 추출 역할 검증 추가
    @GetMapping
    public ResponseEntity<MessageTemplateListResponse> getMessageTemplates(
            Long memberId,
            @PathVariable Long projectId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<MessageTemplateListResult> resultPage =
                messageTemplateService.getMessageTemplateList(projectId, pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(MessageTemplateListResponse.from(resultPage));
    }

    // TODO memberId 추출 역할 검증 추가
    @GetMapping("/{templateId}")
    public ResponseEntity<MessageTemplateDetailResponse> getMessageTemplateDetail(
            Long memberId,
            @PathVariable Long projectId,
            @PathVariable Long templateId
    ) {
        MessageTemplateDetailResult result = messageTemplateService.getMessageTemplate(templateId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(MessageTemplateDetailResponse.from(result));
    }

    // TODO memberId 추출 역할 검증 추가
    @PutMapping("/{templateId}")
    public ResponseEntity<MessageTemplateCreateResponse> updateMessageTemplate(
            Long memberId,
            @PathVariable Long projectId,
            @PathVariable Long templateId,
            @RequestBody @Valid MessageTemplateCreateRequest request
    ) {
        Long messageTemplateId = messageTemplateService.updateMessageTemplate(
                request.toUpdateCommand(projectId, memberId, templateId));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new MessageTemplateCreateResponse(messageTemplateId));
    }

    // TODO memberId 추출 역할 검증 추가
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteMessageTemplate(
            Long memberId,
            @PathVariable Long projectId,
            @PathVariable Long templateId
    ) {
        messageTemplateService.deleteMessageTemplate(templateId, projectId);
        return ResponseEntity.noContent().build();
    }
}
