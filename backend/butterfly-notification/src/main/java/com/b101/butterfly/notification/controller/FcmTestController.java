package com.b101.butterfly.notification.controller;

import com.b101.butterfly.notification.dto.request.FcmSendRequest;
import com.b101.butterfly.notification.dto.response.FcmSendResponse;
import com.b101.butterfly.notification.service.fcm.FcmService;
import com.b101.butterfly.global.response.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FCM 발송 테스트용 컨트롤러. 운영 배포 시 제거할 것.
 */
@RestController
@RequestMapping("/api/test/fcm")
@RequiredArgsConstructor
public class FcmTestController {

    private static final Long TEST_PROJECT_ID = 0L;

    private final FcmService fcmService;
    private final ObjectMapper objectMapper;

    /**
     * credentialJson을 JSON 객체 그대로 받아서 내부적으로 문자열 변환.
     * 이스케이프 없이 바로 테스트 가능.
     */
    public record TestFcmRequest(
            JsonNode credentialJson,
            List<String> tokens,
            String title,
            String body,
            String imageUrl
    ) {}

    @PostMapping("/send")
    public ApiResponse<FcmSendResponse> testSend(@RequestBody TestFcmRequest request)
            throws JsonProcessingException {

        String credentialStr = objectMapper.writeValueAsString(request.credentialJson());

        FcmSendRequest fcmRequest = new FcmSendRequest(
                credentialStr,
                request.tokens(),
                request.title(),
                request.body(),
                request.imageUrl()
        );

        return ApiResponse.ok(fcmService.sendAll(TEST_PROJECT_ID, fcmRequest));
    }
}
