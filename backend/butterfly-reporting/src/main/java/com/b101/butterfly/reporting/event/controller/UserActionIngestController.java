package com.b101.butterfly.reporting.event.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.reporting.event.dto.UserActionIngestRequest;
import com.b101.butterfly.reporting.event.dto.UserActionIngestResponse;
import com.b101.butterfly.reporting.event.entity.UserActionLog;
import com.b101.butterfly.reporting.event.service.UserActionIngestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 반응 이벤트 수신 컨트롤러 (API #19 — butterfly_api_spec_v2.md).
 *
 * <p>고객사 앱 클라이언트가 발행한 OPEN/CLICK/CONVERT/DISMISS 이벤트를 받아 user_action_logs 에
 * 적재한다. 본 컨트롤러는 POST 만 제공해 DELETE/UPDATE 가 자동으로 405 Method Not Allowed 로 차단된다.
 *
 * <p>인증/레이트 리밋: {@code ApiKeyAuthenticationFilter}(역할 1, S14P31B101-205)가
 * {@code X-API-Key} 헤더를 검증해 request attribute {@code resolvedProjectId} 에 projectId 를
 * 설정한다. 본 컨트롤러는 그 값을 꺼내 서비스로 전달하고, 서비스는 logId.projectId 와 비교해
 * 다른 프로젝트 logId 사용 시도를 차단한다 (ADR-0017 §"도메인 검증").
 */
@RestController
@Profile("api")
@RequestMapping("/api/v1/events/actions")
@RequiredArgsConstructor
public class UserActionIngestController {

    /** {@code ApiKeyAuthenticationFilter.RESOLVED_PROJECT_ID} 와 동일한 키. reporting 모듈은
     *  butterfly-api 의존이 없어 상수 문자열을 그대로 사용 — 양쪽 변경 시 동기화 필요. */
    private static final String RESOLVED_PROJECT_ID_ATTR = "resolvedProjectId";

    private final UserActionIngestService userActionIngestService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserActionIngestResponse>> ingest(
            @Valid @RequestBody UserActionIngestRequest request,
            HttpServletRequest httpRequest
    ) {
        Long resolvedProjectId = (Long) httpRequest.getAttribute(RESOLVED_PROJECT_ID_ATTR);
        UserActionLog saved = userActionIngestService.record(request, resolvedProjectId);
        UserActionIngestResponse body = new UserActionIngestResponse(saved.getId(), saved.getCreatedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(body));
    }
}
