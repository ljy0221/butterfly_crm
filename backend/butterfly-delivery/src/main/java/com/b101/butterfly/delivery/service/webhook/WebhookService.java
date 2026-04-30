package com.b101.butterfly.delivery.service.webhook;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.dto.response.DeliveryResult;
import com.b101.butterfly.delivery.exception.DeliveryErrorCode;
import com.b101.butterfly.delivery.util.HmacUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";

    private final RestClient deliveryRestClient;
    private final ObjectMapper objectMapper;

    public DeliveryResult sendOne(Long projectId,
                                  Long campaignId,
                                  DeliveryCommand.WebhookCredential credential,
                                  DeliveryCommand.MessagePayload message,
                                  DeliveryCommand.DeliveryTarget target) {
        if (credential.url() == null || credential.url().isBlank()) {
            return DeliveryResult.permanentFailure(
                    DeliveryErrorCode.WEBHOOK_URL_MISSING.getCode(),
                    DeliveryErrorCode.WEBHOOK_URL_MISSING.getMessage());
        }

        DeliveryCommand.WebhookFormat format = credential.format() != null
                ? credential.format() : DeliveryCommand.WebhookFormat.DEFAULT;
        Map<String, Object> payload = buildPayload(format, projectId, campaignId, message, target);

        try {
            RestClient.RequestBodySpec request = deliveryRestClient.post()
                    .uri(credential.url())
                    .contentType(MediaType.APPLICATION_JSON);

            if (credential.secret() != null && !credential.secret().isBlank()) {
                String payloadJson = toJson(payload);
                String signature = HmacUtils.hmacSha256(credential.secret(), payloadJson);
                request.header(SIGNATURE_HEADER, signature);
            }

            request.body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Webhook 발송 성공: url={}, userId={}", credential.url(), target.userId());
            return DeliveryResult.success();
        } catch (Exception e) {
            log.warn("Webhook 발송 실패: url={}, userId={}, error={}",
                    credential.url(), target.userId(), e.getMessage(), e);
            return DeliveryResult.transientFailure(
                    DeliveryErrorCode.WEBHOOK_SEND_FAILED.getCode(), e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(DeliveryCommand.WebhookFormat format,
                                             Long projectId,
                                             Long campaignId,
                                             DeliveryCommand.MessagePayload message,
                                             DeliveryCommand.DeliveryTarget target) {
        return switch (format) {
            case MATTERMOST -> buildMattermostPayload(message);
            case SLACK -> buildSlackPayload(message);
            default -> buildDefaultPayload(projectId, campaignId, message, target);
        };
    }

    private Map<String, Object> buildDefaultPayload(Long projectId,
                                                    Long campaignId,
                                                    DeliveryCommand.MessagePayload message,
                                                    DeliveryCommand.DeliveryTarget target) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("campaignId", campaignId);
        payload.put("userId", target.userId());
        payload.put("title", message.title());
        payload.put("body", message.body());
        if (message.imageUrl() != null) {
            payload.put("imageUrl", message.imageUrl());
        }
        if (message.clickAction() != null) {
            payload.put("clickAction", message.clickAction());
        }
        if (message.data() != null) {
            payload.put("data", message.data());
        }
        return payload;
    }

    private Map<String, Object> buildMattermostPayload(DeliveryCommand.MessagePayload message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        if (message.title() != null && !message.title().isBlank()) {
            text.append("#### ").append(message.title()).append("\n");
        }
        text.append(message.body());
        if (message.imageUrl() != null && !message.imageUrl().isBlank()) {
            text.append("\n![image](").append(message.imageUrl()).append(")");
        }
        if (message.clickAction() != null && !message.clickAction().isBlank()) {
            text.append("\n[자세히 보기](").append(message.clickAction()).append(")");
        }
        payload.put("text", text.toString());
        payload.put("username", "Butterfly");
        return payload;
    }

    private Map<String, Object> buildSlackPayload(DeliveryCommand.MessagePayload message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        if (message.title() != null && !message.title().isBlank()) {
            text.append("*").append(message.title()).append("*\n");
        }
        text.append(message.body());
        if (message.clickAction() != null && !message.clickAction().isBlank()) {
            text.append("\n<").append(message.clickAction()).append("|자세히 보기>");
        }
        payload.put("text", text.toString());
        payload.put("username", "Butterfly");
        if (message.imageUrl() != null && !message.imageUrl().isBlank()) {
            payload.put("icon_url", message.imageUrl());
        }
        return payload;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }
}
