package com.b101.butterfly.delivery.service.fcm;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.dto.request.FcmSendRequest;
import com.b101.butterfly.delivery.dto.response.DeliveryResult;
import com.b101.butterfly.delivery.exception.DeliveryErrorCode;
import com.b101.butterfly.delivery.dto.response.FcmSendResponse;
import com.b101.butterfly.delivery.dto.response.FcmTokenResult;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
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

    public DeliveryResult sendOne(Long projectId, String credentialJson,
                                  DeliveryCommand.MessagePayload messagePayload,
                                  DeliveryCommand.DeliveryTarget target) {
        if (target.pushToken() == null || target.pushToken().isBlank()) {
            return DeliveryResult.permanentFailure(
                    DeliveryErrorCode.FCM_TOKEN_MISSING.getCode(),
                    DeliveryErrorCode.FCM_TOKEN_MISSING.getMessage());
        }

        FirebaseMessaging messaging = FirebaseMessaging.getInstance(
                fcmAppManager.getOrCreateApp(projectId, credentialJson));

        Message.Builder messageBuilder = Message.builder()
                .setToken(target.pushToken())
                .setNotification(Notification.builder()
                        .setTitle(messagePayload.title())
                        .setBody(messagePayload.body())
                        .setImage(messagePayload.imageUrl())
                        .build());

        if (messagePayload.clickAction() != null) {
            messageBuilder.putData("clickAction", messagePayload.clickAction());
        }

        if (messagePayload.data() != null) {
            messageBuilder.putData("customData", messagePayload.data());
        }

        try {
            String messageId = messaging.send(messageBuilder.build());
            log.debug("FCM 발송 성공: messageId={}, projectId={}, userId={}",
                    messageId, projectId, target.userId());
            return DeliveryResult.success();
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 발송 실패: projectId={}, userId={}, errorCode={}",
                    projectId, target.userId(), e.getMessagingErrorCode(), e);
            return mapFcmError(e);
        }
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
