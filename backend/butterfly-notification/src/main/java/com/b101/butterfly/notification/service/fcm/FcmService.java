package com.b101.butterfly.notification.service.fcm;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.request.FcmSendRequest;
import com.b101.butterfly.notification.dto.response.DeliveryResult;
import com.b101.butterfly.notification.dto.response.FcmSendResponse;
import com.b101.butterfly.notification.dto.response.FcmTokenResult;
import com.b101.butterfly.notification.dto.response.TargetOutcome;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private static final int BATCH_SIZE = 500;

    private final FcmAppManager fcmAppManager;

    public FcmSendResponse sendAll(Long projectId, FcmSendRequest request) {
        FirebaseMessaging messaging = FirebaseMessaging.getInstance(
                fcmAppManager.getOrCreateApp(projectId, request.credentialJson()));

        Notification notification = Notification.builder()
                .setTitle(request.title())
                .setBody(request.body())
                .setImage(request.imageUrl())
                .build();

        List<FcmTokenResult> allResults = new ArrayList<>();
        List<String> tokens = request.tokens();

        for (int i = 0; i < tokens.size(); i += BATCH_SIZE) {
            List<String> chunk = tokens.subList(i, Math.min(i + BATCH_SIZE, tokens.size()));

            MulticastMessage multicastMessage = MulticastMessage.builder()
                    .addAllTokens(chunk)
                    .setNotification(notification)
                    .build();

            try {
                BatchResponse batchResponse = messaging.sendEachForMulticast(multicastMessage);
                List<SendResponse> responses = batchResponse.getResponses();

                for (int j = 0; j < responses.size(); j++) {
                    SendResponse resp = responses.get(j);
                    String token = chunk.get(j);
                    if (resp.isSuccessful()) {
                        allResults.add(FcmTokenResult.success(token, resp.getMessageId()));
                    } else {
                        FirebaseMessagingException ex = resp.getException();
                        String errorCode = ex != null && ex.getMessagingErrorCode() != null
                                ? ex.getMessagingErrorCode().name() : "UNKNOWN";
                        String errorMessage = ex != null ? ex.getMessage() : "Unknown error";
                        allResults.add(FcmTokenResult.failure(token, errorCode, errorMessage));
                    }
                }
            } catch (FirebaseMessagingException e) {
                log.error("FCM 배치 발송 실패: projectId={}, chunkStart={}", projectId, i, e);
                for (String token : chunk) {
                    allResults.add(FcmTokenResult.failure(token, "BATCH_ERROR", e.getMessage()));
                }
            }
        }

        int successCount = (int) allResults.stream().filter(FcmTokenResult::successful).count();
        int failureCount = allResults.size() - successCount;

        log.info("FCM 발송 완료: projectId={}, success={}, failure={}", projectId, successCount, failureCount);
        return new FcmSendResponse(successCount, failureCount, allResults);
    }

    public List<TargetOutcome> sendBatch(Long projectId, String credentialJson,
                                         DeliveryCommand.MessagePayload messagePayload,
                                         List<DeliveryCommand.DeliveryTarget> targets) {
        List<TargetOutcome> outcomes = new ArrayList<>();

        // 토큰 없는 대상은 즉시 실패 처리, 유효한 대상만 모아서 배치 발송
        List<DeliveryCommand.DeliveryTarget> validTargets = new ArrayList<>();
        for (DeliveryCommand.DeliveryTarget target : targets) {
            if (target.pushToken() == null || target.pushToken().isBlank()) {
                outcomes.add(new TargetOutcome(target, DeliveryResult.permanentFailure(
                        DeliveryErrorCode.FCM_TOKEN_MISSING.getCode(),
                        DeliveryErrorCode.FCM_TOKEN_MISSING.getMessage())));
            } else {
                validTargets.add(target);
            }
        }

        if (validTargets.isEmpty()) {
            return outcomes;
        }

        FirebaseMessaging messaging = FirebaseMessaging.getInstance(
                fcmAppManager.getOrCreateApp(projectId, credentialJson));

        Notification notification = Notification.builder()
                .setTitle(messagePayload.title())
                .setBody(messagePayload.body())
                .setImage(messagePayload.imageUrl())
                .build();

        for (int i = 0; i < validTargets.size(); i += BATCH_SIZE) {
            List<DeliveryCommand.DeliveryTarget> chunk =
                    validTargets.subList(i, Math.min(i + BATCH_SIZE, validTargets.size()));
            List<String> tokens = chunk.stream()
                    .map(DeliveryCommand.DeliveryTarget::pushToken)
                    .toList();

            MulticastMessage.Builder builder = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(notification);

            if (messagePayload.clickAction() != null) {
                builder.putData("clickAction", messagePayload.clickAction());
            }
            if (messagePayload.data() != null) {
                builder.putData("customData", messagePayload.data());
            }

            try {
                BatchResponse batchResponse = messaging.sendEachForMulticast(builder.build());
                List<SendResponse> responses = batchResponse.getResponses();

                for (int j = 0; j < responses.size(); j++) {
                    SendResponse resp = responses.get(j);
                    DeliveryCommand.DeliveryTarget target = chunk.get(j);
                    if (resp.isSuccessful()) {
                        outcomes.add(new TargetOutcome(target, DeliveryResult.success()));
                    } else {
                        outcomes.add(new TargetOutcome(target, mapFcmError(resp.getException())));
                    }
                }
            } catch (FirebaseMessagingException e) {
                log.error("FCM 배치 발송 실패: projectId={}, chunkStart={}", projectId, i, e);
                DeliveryResult error = mapFcmError(e);
                for (DeliveryCommand.DeliveryTarget target : chunk) {
                    outcomes.add(new TargetOutcome(target, error));
                }
            }
        }

        long successCount = outcomes.stream().filter(o -> o.result().successful()).count();
        log.info("FCM 배치 발송 완료: projectId={}, success={}, failure={}",
                projectId, successCount, outcomes.size() - successCount);
        return outcomes;
    }

    private DeliveryResult mapFcmError(FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        String errorCode = code != null ? code.name() : "UNKNOWN";
        String errorMessage = e.getMessage();

        if (code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT) {
            return DeliveryResult.permanentFailure(errorCode, errorMessage);
        }

        return DeliveryResult.transientFailure(errorCode, errorMessage);
    }
}
