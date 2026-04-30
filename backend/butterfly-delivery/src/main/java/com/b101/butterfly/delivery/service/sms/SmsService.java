package com.b101.butterfly.delivery.service.sms;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.dto.response.DeliveryResult;
import com.b101.butterfly.delivery.exception.DeliveryErrorCode;
import com.b101.butterfly.delivery.util.HmacUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.time.Instant;
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

    public DeliveryResult sendOne(DeliveryCommand.SmsCredential credential,
                                  DeliveryCommand.MessagePayload message,
                                  DeliveryCommand.DeliveryTarget target) {
        if (target.phoneNumber() == null || target.phoneNumber().isBlank()) {
            return DeliveryResult.permanentFailure(
                    DeliveryErrorCode.SMS_PHONE_MISSING.getCode(),
                    DeliveryErrorCode.SMS_PHONE_MISSING.getMessage());
        }

        String text = buildMessageText(message);
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
        if (message.clickAction() != null && !message.clickAction().isBlank()) {
            sb.append("\n").append(message.clickAction());
        }
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
