package com.b101.butterfly.reporting.event.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.reporting.event.dto.UserActionIngestRequest;
import com.b101.butterfly.reporting.event.dto.UserActionIngestResponse;
import com.b101.butterfly.reporting.event.entity.UserActionLog;
import com.b101.butterfly.reporting.event.service.UserActionIngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 반응 이벤트 수신 컨트롤러 (API #19 — butterfly_api_spec_v2.md).
 *
 * <p>SDK 발행 OPEN/CLICK/CONVERT/DISMISS 이벤트를 받아 user_action_logs 에 적재한다.
 * 본 컨트롤러는 POST 만 제공해 DELETE/UPDATE 가 자동으로 405 Method Not Allowed로 차단된다.
 *
 * <p>인증(Public API Key)·레이트 리밋·부하 검증은 본 티켓 범위 외 — S14P31B101-193 (123-B)
 * 에서 외부 팀 인터페이스 합의 후 추가된다. 본 PR(123-A)에서는 인증 모듈 미완성 공유 이슈와
 * 동일하게 임시 허용 상태로 두고, 적재 구조·서비스 계약·검증 로직만 정착한다.
 */
@RestController
@RequestMapping("/api/v1/events/actions")
@RequiredArgsConstructor
public class UserActionIngestController {

    private final UserActionIngestService userActionIngestService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserActionIngestResponse>> ingest(
            @Valid @RequestBody UserActionIngestRequest request
    ) {
        UserActionLog saved = userActionIngestService.record(request);
        UserActionIngestResponse body = new UserActionIngestResponse(saved.getId(), saved.getCreatedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(body));
    }
}
