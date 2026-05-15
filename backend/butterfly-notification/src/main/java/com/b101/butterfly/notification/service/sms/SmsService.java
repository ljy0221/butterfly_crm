package com.b101.butterfly.notification.service.sms;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.response.DeliveryResult;
import com.b101.butterfly.notification.dto.response.TargetOutcome;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.b101.butterfly.notification.util.HmacUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private static final String COOLSMS_API_URL = "https://api.coolsms.co.kr/messages/v4/send-many";
    private static final int EUC_KR_SMS_LIMIT = 90;
    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    private final RestClient deliveryRestClient;

    public List<TargetOutcome> sendBatch(DeliveryCommand.SmsCredential credential,
                                         DeliveryCommand.MessagePayload message,
                                         List<DeliveryCommand.DeliveryTarget> targets) {
        List<TargetOutcome> outcomes = new ArrayList<>();

        // 번호 없는 대상은 즉시 실패 처리, 유효한 대상만 모아서 배치 발송
        List<DeliveryCommand.DeliveryTarget> validTargets = new ArrayList<>();
        for (DeliveryCommand.DeliveryTarget target : targets) {
            if (target.phoneNumber() == null || target.phoneNumber().isBlank()) {
                outcomes.add(new TargetOutcome(target, DeliveryResult.permanentFailure(
                        DeliveryErrorCode.SMS_PHONE_MISSING.getCode(),
                        DeliveryErrorCode.SMS_PHONE_MISSING.getMessage())));
            } else {
                validTargets.add(target);
            }
        }

        if (validTargets.isEmpty()) {
            return outcomes;
        }

        // 개인화 변수 치환 — target 마다 다른 text 가능
        Object[] messageMaps = validTargets.stream()
                .map(target -> {
                    String text = buildMessageText(message.personalize(target.variables()));
                    String type = determineMessageType(text);
                    return Map.of(
                            "to", target.phoneNumber(),
                            "from", credential.senderNumber(),
                            "text", text,
                            "type", type
                    );
                })
                .toArray();

        Map<String, Object> body = Map.of("messages", messageMaps);
        String authHeader = buildAuthHeader(credential.apiKey(), credential.apiSecret());

        try {
            deliveryRestClient.post()
                    .uri(COOLSMS_API_URL)
                    .header("Authorization", authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("SMS 배치 발송 성공: count={}", validTargets.size());
            for (DeliveryCommand.DeliveryTarget target : validTargets) {
                outcomes.add(new TargetOutcome(target, DeliveryResult.success()));
            }
        } catch (Exception e) {
            log.warn("SMS 배치 발송 실패: count={}, error={}", validTargets.size(), e.getMessage(), e);
            DeliveryResult error = mapSmsError(e);
            for (DeliveryCommand.DeliveryTarget target : validTargets) {
                outcomes.add(new TargetOutcome(target, error));
            }
        }

        return outcomes;
    }

    public DeliveryResult sendOne(DeliveryCommand.SmsCredential credential,
                                  DeliveryCommand.MessagePayload message,
                                  DeliveryCommand.DeliveryTarget target) {
        if (target.phoneNumber() == null || target.phoneNumber().isBlank()) {
            return DeliveryResult.permanentFailure(
                    DeliveryErrorCode.SMS_PHONE_MISSING.getCode(),
                    DeliveryErrorCode.SMS_PHONE_MISSING.getMessage());
        }

        // 개인화 변수 치환
        DeliveryCommand.MessagePayload personalized = message.personalize(target.variables());
        String text = buildMessageText(personalized);
        String type = determineMessageType(text);

        Map<String, Object> messageMap = Map.of(
                "to", target.phoneNumber(),
                "from", credential.senderNumber(),
                "text", text,
                "type", type
        );

        Map<String, Object> body = Map.of("messages", new Object[]{messageMap});

        String authHeader = buildAuthHeader(credential.apiKey(), credential.apiSecret());

        try {
            deliveryRestClient.post()
                    .uri(COOLSMS_API_URL)
                    .header("Authorization", authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("SMS 발송 성공: to={}, userId={}, type={}", target.phoneNumber(), target.userId(), type);
            return DeliveryResult.success();
        } catch (Exception e) {
            log.warn("SMS 발송 실패: to={}, userId={}, error={}", target.phoneNumber(), target.userId(), e.getMessage(), e);
            return mapSmsError(e);
        }
    }

    private String buildMessageText(DeliveryCommand.MessagePayload message) {
        StringBuilder sb = new StringBuilder();
        if (message.title() != null && !message.title().isBlank()) {
            sb.append("[").append(message.title()).append("] ");
        }
        sb.append(message.body());
        return sb.toString();
    }

    String determineMessageType(String text) {
        int byteLength = text.getBytes(EUC_KR).length;
        return byteLength <= EUC_KR_SMS_LIMIT ? "SMS" : "LMS";
    }

    String buildAuthHeader(String apiKey, String apiSecret) {
        String date = Instant.now().toString();
        String salt = UUID.randomUUID().toString();
        String signature = HmacUtils.hmacSha256(apiSecret, date + salt);
        return String.format("HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s",
                apiKey, date, salt, signature);
    }

    private DeliveryResult mapSmsError(Exception e) {
        String message = e.getMessage();
        if (message != null && (message.contains("InvalidPhoneNumber") || message.contains("ValidationError"))) {
            return DeliveryResult.permanentFailure(
                    DeliveryErrorCode.SMS_INVALID_NUMBER.getCode(), message);
        }
        return DeliveryResult.transientFailure(
                DeliveryErrorCode.SMS_SEND_FAILED.getCode(), message);
    }
}
