package com.b101.butterfly.reporting.notification.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.reporting.notification.dto.NotificationLogIngestRequest;
import com.b101.butterfly.reporting.notification.dto.NotificationLogIngestResponse;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import com.b101.butterfly.reporting.notification.service.NotificationLogIngestResult;
import com.b101.butterfly.reporting.notification.service.NotificationLogIngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발송 결과 적재 HTTP 대안 엔드포인트 (API #30 — butterfly_api_spec_v2.md).
 *
 * <p>Kafka 토픽이 가용하지 않을 때 발송 워커가 직접 호출한다. 멱등 키({@code messageKey})로
 * dedup되어 같은 키를 재호출하면 200 + 기존 logId, 신규 키는 201 + 새 logId.
 *
 * <p>외부 노출은 게이트웨이/네트워크 정책으로 차단하고, 본 컨트롤러는 추가로
 * `X-Internal-Token` 헤더를 검증한다 ({@link InternalTokenGuard}).
 */
@RestController
@Profile("api")
@RequestMapping("/internal/notification-logs")
@RequiredArgsConstructor
public class NotificationLogIngestController {

    private final NotificationLogIngestService ingestService;
    private final InternalTokenGuard internalTokenGuard;

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationLogIngestResponse>> ingest(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @Valid @RequestBody NotificationLogIngestRequest request
    ) {
        internalTokenGuard.verify(internalToken);

        NotificationLogIngestResult result = ingestService.record(request);
        NotificationLog log = result.log();
        NotificationLogIngestResponse body = new NotificationLogIngestResponse(log.getId(), log.getCreatedAt());

        HttpStatus status = result.deduplicated() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.ok(body));
    }
}
