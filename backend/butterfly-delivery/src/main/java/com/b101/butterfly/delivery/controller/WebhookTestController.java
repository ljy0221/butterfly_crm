package com.b101.butterfly.delivery.controller;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.dto.response.DeliveryResult;
import com.b101.butterfly.delivery.service.webhook.WebhookService;
import com.b101.butterfly.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook 발송 테스트용 컨트롤러. 운영 배포 시 제거할 것.
 */
@RestController
@RequestMapping("/api/test/webhook")
@RequiredArgsConstructor
public class WebhookTestController {

    private static final Long TEST_PROJECT_ID = 0L;
    private static final Long TEST_CAMPAIGN_ID = 0L;

    private final WebhookService webhookService;

    public record TestWebhookRequest(
            String url,
            String secret,
            DeliveryCommand.WebhookFormat format,
            String title,
            String body,
            String imageUrl,
            String clickAction,
            String data
    ) {}

    @PostMapping("/send")
    public ApiResponse<DeliveryResult> testSend(@RequestBody TestWebhookRequest request) {
        DeliveryCommand.WebhookFormat format = request.format() != null
                ? request.format() : DeliveryCommand.WebhookFormat.DEFAULT;
        DeliveryCommand.WebhookCredential credential = new DeliveryCommand.WebhookCredential(
                request.url(), request.secret(), format);

        DeliveryCommand.MessagePayload message = new DeliveryCommand.MessagePayload(
                request.title(), request.body(), request.imageUrl(), request.clickAction(), request.data());

        DeliveryCommand.DeliveryTarget target = new DeliveryCommand.DeliveryTarget(
                0L, null, null, null, null);

        return ApiResponse.ok(webhookService.sendOne(
                TEST_PROJECT_ID, TEST_CAMPAIGN_ID, credential, message, target));
    }
}
