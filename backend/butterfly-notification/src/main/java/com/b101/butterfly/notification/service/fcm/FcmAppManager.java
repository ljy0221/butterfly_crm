package com.b101.butterfly.notification.service.fcm;

import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FcmAppManager {

    private final ConcurrentHashMap<Long, FirebaseApp> appCache = new ConcurrentHashMap<>();

    public FirebaseApp getOrCreateApp(Long projectId, String credentialJson) {
        return appCache.computeIfAbsent(projectId, id -> createApp(id, credentialJson));
    }

    public void invalidate(Long projectId) {
        FirebaseApp app = appCache.remove(projectId);
        if (app != null) {
            app.delete();
            log.info("FirebaseApp 캐시 무효화: projectId={}", projectId);
        }
    }

    @PreDestroy
    public void invalidateAll() {
        appCache.forEach((id, app) -> {
            try {
                app.delete();
            } catch (Exception e) {
                log.warn("FirebaseApp 삭제 실패: projectId={}", id, e);
            }
        });
        appCache.clear();
        log.info("모든 FirebaseApp 캐시 정리 완료");
    }

    private FirebaseApp createApp(Long projectId, String credentialJson) {
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(credentialJson.getBytes(StandardCharsets.UTF_8)));

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            String appName = "project-" + projectId;
            FirebaseApp app = FirebaseApp.initializeApp(options, appName);
            log.info("FirebaseApp 생성: projectId={}, appName={}", projectId, appName);
            return app;
        } catch (IOException e) {
            log.error("FCM credential 파싱 실패: projectId={}", projectId, e);
            throw new BusinessException(DeliveryErrorCode.FCM_CREDENTIALS_INVALID, e);
        }
    }
}
