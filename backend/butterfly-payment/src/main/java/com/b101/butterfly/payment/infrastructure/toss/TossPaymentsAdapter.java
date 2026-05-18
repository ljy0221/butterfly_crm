package com.b101.butterfly.payment.infrastructure.toss;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.payment.config.TossPaymentsProperties;
import com.b101.butterfly.payment.domain.enums.PaymentMethod;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import com.b101.butterfly.payment.port.PaymentConfirmResult;
import com.b101.butterfly.payment.port.PaymentGatewayPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 토스페이먼츠 v2 confirm 어댑터.
 *
 * <p>핵심 컨벤션:
 * <ul>
 *   <li>Authorization: {@code Basic base64(secretKey + ":")} — 콜론 누락이 흔한 버그라 별도 메서드로 분리</li>
 *   <li>{@code Idempotency-Key} 헤더: {@code orderId} 사용 (Toss 측 중복 confirm 방지)</li>
 *   <li>응답 status 가 {@code DONE} 인 경우만 {@link PaymentConfirmResult.Status#DONE} 으로 매핑</li>
 *   <li>{@code 4xx} 응답은 {@code FAILED} (PG 가 결제 거절), {@code 5xx}/네트워크는 {@code BAD_GATEWAY}</li>
 * </ul>
 */
@Slf4j
@Component
public class TossPaymentsAdapter implements PaymentGatewayPort {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String authorizationHeader;

    public TossPaymentsAdapter(TossPaymentsProperties properties,
                               RestClient.Builder builder,
                               ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.objectMapper = objectMapper;
        this.authorizationHeader = buildBasicAuthHeader(properties.secretKey());
    }

    @Override
    public PaymentConfirmResult confirm(String orderId, String paymentKey, Long amount) {
        Map<String, Object> body = Map.of(
                "orderId", orderId,
                "paymentKey", paymentKey,
                "amount", amount
        );
        try {
            String responseBody = restClient.post()
                    .uri(CONFIRM_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .header("Idempotency-Key", orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseSuccess(responseBody);
        } catch (HttpClientErrorException e) {
            return parseRejection(e.getStatusCode(), e.getResponseBodyAsString());
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.warn("Toss confirm gateway unavailable orderId={} cause={}", orderId, e.toString());
            throw new BusinessException(PaymentErrorCode.PAY_GATEWAY_UNAVAILABLE);
        }
    }

    /**
     * {@code Basic base64(secretKey + ":")} 형식의 Authorization 헤더.
     * 콜론 누락은 Toss 통합의 가장 흔한 실수.
     */
    static String buildBasicAuthHeader(String secretKey) {
        String token = secretKey + ":";
        String encoded = Base64.getEncoder()
                .encodeToString(token.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private PaymentConfirmResult parseSuccess(String responseBody) {
        JsonNode root = readTree(responseBody);
        String tossStatus = textOrNull(root, "status");
        if (!"DONE".equals(tossStatus)) {
            return new PaymentConfirmResult(
                    PaymentConfirmResult.Status.OTHER,
                    null, null, null,
                    null, "unexpected status: " + tossStatus,
                    responseBody
            );
        }
        return new PaymentConfirmResult(
                PaymentConfirmResult.Status.DONE,
                textOrNull(root, "paymentKey"),
                mapMethod(textOrNull(root, "method")),
                parseDateTime(textOrNull(root, "approvedAt")),
                null, null,
                responseBody
        );
    }

    private PaymentConfirmResult parseRejection(HttpStatusCode statusCode, String responseBody) {
        JsonNode root = responseBody == null ? null : readTree(responseBody);
        String code = root == null ? null : textOrNull(root, "code");
        String message = root == null ? null : textOrNull(root, "message");
        log.info("Toss confirm rejected status={} code={} message={}", statusCode, code, message);
        if (statusCode == null || statusCode.is5xxServerError()) {
            throw new BusinessException(PaymentErrorCode.PAY_GATEWAY_UNAVAILABLE);
        }
        return new PaymentConfirmResult(
                PaymentConfirmResult.Status.FAILED,
                null, null, null,
                code, message,
                responseBody
        );
    }

    private JsonNode readTree(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.warn("Toss response parse failure: {}", e.toString());
            throw new BusinessException(PaymentErrorCode.PAY_GATEWAY_UNAVAILABLE);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static LocalDateTime parseDateTime(String iso) {
        if (iso == null) return null;
        try {
            return OffsetDateTime.parse(iso).toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Toss v2 응답의 한국어 method 문자열을 enum 으로 매핑. 매핑 실패 시 null. */
    private static PaymentMethod mapMethod(String tossMethod) {
        if (tossMethod == null) return null;
        return switch (tossMethod) {
            case "카드" -> PaymentMethod.CARD;
            case "간편결제" -> PaymentMethod.EASY_PAY;
            case "가상계좌" -> PaymentMethod.VIRTUAL_ACCOUNT;
            case "휴대폰" -> PaymentMethod.MOBILE_PHONE;
            case "계좌이체" -> PaymentMethod.TRANSFER;
            case "문화상품권", "도서문화상품권", "게임문화상품권" -> PaymentMethod.GIFT_CERTIFICATE;
            default -> null;
        };
    }
}
