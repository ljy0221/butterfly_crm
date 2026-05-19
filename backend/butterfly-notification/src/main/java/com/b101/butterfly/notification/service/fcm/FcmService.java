package com.b101.butterfly.notification.service.fcm;

import com.b101.butterfly.campaign.messagetemplate.domain.VariableSubstitutor;
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
            log.warn("FCM 발송 가능한 토큰이 없음: projectId={}, 전체={}, 토큰없음={}",
                    projectId, targets.size(), outcomes.size());
            return outcomes;
        }

        FirebaseMessaging messaging = FirebaseMessaging.getInstance(
                fcmAppManager.getOrCreateApp(projectId, credentialJson));

        // 개인화 변수 분기 — 본 sendBatch 호출 단위로 한 번만 체크 (S14P31B101-289).
        //   - template 에 {{var}} 가 하나도 없으면 multicast 유지 (FCM 서버 fan-out 효율 보존).
        //   - 하나라도 있으면 per-target sendEach (target.variables 별 다른 message — 정확한 개인화).
        boolean hasVariables = VariableSubstitutor.hasVariablePattern(messagePayload.title())
                || VariableSubstitutor.hasVariablePattern(messagePayload.body())
                || VariableSubstitutor.hasVariablePattern(messagePayload.imageUrl());

        Notification notification = hasVariables ? null : Notification.builder()
                .setTitle(messagePayload.title())
                .setBody(messagePayload.body())
                .setImage(messagePayload.imageUrl())
                .build();

        for (int i = 0; i < validTargets.size(); i += BATCH_SIZE) {
            List<DeliveryCommand.DeliveryTarget> chunk =
                    validTargets.subList(i, Math.min(i + BATCH_SIZE, validTargets.size()));

            try {
                BatchResponse batchResponse = hasVariables
                        ? sendEachPersonalized(messaging, messagePayload, chunk)
                        : sendMulticast(messaging, notification, messagePayload, chunk);
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

    /**
     * 변수 없는 경우의 multicast 발송 — 토큰 N 개 + 1 message (FCM 서버 fan-out 효율).
     */
    private BatchResponse sendMulticast(FirebaseMessaging messaging,
                                        Notification notification,
                                        DeliveryCommand.MessagePayload messagePayload,
                                        List<DeliveryCommand.DeliveryTarget> chunk
    ) throws FirebaseMessagingException {
        List<String> tokens = chunk.stream()
                .map(DeliveryCommand.DeliveryTarget::pushToken)
                .toList();
        MulticastMessage.Builder builder = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(notification);
        if (messagePayload.data() != null) {
            builder.putData("customData", messagePayload.data());
        }
        return messaging.sendEachForMulticast(builder.build());
    }

    /**
     * 변수 있는 경우의 per-target send — 각 target 의 variables 로 치환된 별도 Message 를 만들어
     * {@code sendEach(List<Message>)} 호출 (multicast 단일 message 의 효율은 잃지만 정확한 개인화).
     */
    private BatchResponse sendEachPersonalized(FirebaseMessaging messaging,
                                               DeliveryCommand.MessagePayload messagePayload,
                                               List<DeliveryCommand.DeliveryTarget> chunk
    ) throws FirebaseMessagingException {
        List<Message> messages = chunk.stream().map(target -> {
            DeliveryCommand.MessagePayload personalized = messagePayload.personalize(target.variables());
            Notification n = Notification.builder()
                    .setTitle(personalized.title())
                    .setBody(personalized.body())
                    .setImage(personalized.imageUrl())
                    .build();
            Message.Builder mb = Message.builder()
                    .setToken(target.pushToken())
                    .setNotification(n);
            if (personalized.data() != null) {
                mb.putData("customData", personalized.data());
            }
            return mb.build();
        }).toList();
        return messaging.sendEach(messages);
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
