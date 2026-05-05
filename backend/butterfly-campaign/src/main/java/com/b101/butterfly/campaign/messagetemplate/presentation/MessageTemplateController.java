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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 메시지 템플릿 REST 진입점.
 *
 * <p>인가: ADR-0005 — {@code @PreAuthorize("@projectPermission.hasRole(#projectId, ROLE)")}로
 * 프로젝트별 역할 검증. memberId는 SecurityContext의 {@link Authentication#getName()}에서 추출
 * (auth 모듈의 CustomUserDetails 직접 import는 모듈 슬라이스 cycle 회피 위해 사용 안 함).
 * 클라이언트 위조 방지 위해 X-Member-Id 헤더는 더 이상 신뢰하지 않음.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/templates")
public class MessageTemplateController {

    private final MessageTemplateService messageTemplateService;

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    @PostMapping
    public ResponseEntity<MessageTemplateCreateResponse> createMessageTemplate(
            @PathVariable Long projectId,
            @RequestBody @Valid MessageTemplateCreateRequest request
    ) {
        Long messageTemplateId = messageTemplateService.createMessageTemplate(
                request.toCommand(projectId, currentMemberId()));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new MessageTemplateCreateResponse(messageTemplateId));
    }

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    @GetMapping
    public ResponseEntity<MessageTemplateListResponse> getMessageTemplates(
            @PathVariable Long projectId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<MessageTemplateListResult> resultPage =
                messageTemplateService.getMessageTemplateList(projectId, pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(MessageTemplateListResponse.from(resultPage));
    }

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    @GetMapping("/{templateId}")
    public ResponseEntity<MessageTemplateDetailResponse> getMessageTemplateDetail(
            @PathVariable Long projectId,
            @PathVariable Long templateId
    ) {
        MessageTemplateDetailResult result = messageTemplateService.getMessageTemplate(projectId, templateId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(MessageTemplateDetailResponse.from(result));
    }

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    @PutMapping("/{templateId}")
    public ResponseEntity<MessageTemplateCreateResponse> updateMessageTemplate(
            @PathVariable Long projectId,
            @PathVariable Long templateId,
            @RequestBody @Valid MessageTemplateCreateRequest request
    ) {
        Long messageTemplateId = messageTemplateService.updateMessageTemplate(
                request.toUpdateCommand(projectId, currentMemberId(), templateId));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new MessageTemplateCreateResponse(messageTemplateId));
    }

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteMessageTemplate(
            @PathVariable Long projectId,
            @PathVariable Long templateId
    ) {
        messageTemplateService.deleteMessageTemplate(templateId, projectId);
        return ResponseEntity.noContent().build();
    }

    /**
     * SecurityContext의 인증 정보에서 memberId 추출.
     *
     * <p>{@code CustomUserDetails.getUsername()}이 {@code String.valueOf(memberId)}를 반환하므로
     * {@link Authentication#getName()}으로 안전하게 얻는다. {@link SecurityContextHolder}를 직접 사용해
     * Spring Security 의 {@code AuthenticationArgumentResolver} 등록 여부와 무관하게 동작한다.
     * 이 우회로 campaign → auth 직접 의존을 피해 모듈 슬라이스 cycle을 차단한다.
     */
    private Long currentMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
