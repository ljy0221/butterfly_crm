package com.b101.butterfly.campaign.messagetemplate.presentation;

import com.b101.butterfly.campaign.messagetemplate.application.MessageTemplateService;
import com.b101.butterfly.campaign.messagetemplate.presentation.dto.MessageTemplateCreateRequest;
import com.b101.butterfly.campaign.messagetemplate.presentation.dto.MessageTemplateCreateResponse;
import jakarta.validation.Valid;
import java.nio.file.attribute.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/v1/templates")
public class MessageTemplateController {

    private final MessageTemplateService messageTemplateService;

    // TODO 아이디 역할 검증 추가
    @PostMapping
    public ResponseEntity<MessageTemplateCreateResponse> createMessageTemplate(
            UserPrincipal userPrincipal,
            @RequestBody @Valid MessageTemplateCreateRequest request
    ) {
        Long messageTemplateId = messageTemplateService.createMessageTemplate(
                request.toCommand(userPrincipal.getMemberId));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new MessageTemplateCreateResponse(messageTemplateId));
    }
}
