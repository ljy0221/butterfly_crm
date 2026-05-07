package com.b101.butterfly.tenant.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.tenant.constant.AuditAction;
import com.b101.butterfly.tenant.entity.Project;
import com.b101.butterfly.tenant.exception.TenantErrorCode;
import com.b101.butterfly.tenant.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class ApiKeyService {

    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public String issueApiKey(Long projectId, Long actorId) {
        Project project = findProject(projectId);

        if (project.getPublicApiKey() != null) {
            throw new BusinessException(TenantErrorCode.API_KEY_ALREADY_EXISTS);
        }

        String apiKey = generateApiKey();
        project.issueApiKey(sha256(apiKey));
        eventPublisher.publishEvent(AuditRecordedEvent.of(
                projectId, actorId, AuditAction.API_KEY_ISSUED, "Project", projectId,
                null, null, Map.of("keyPrefix", apiKey.substring(0, 12) + "...")
        ));
        return apiKey;
    }

    @Transactional
    public String revokeAndReissueApiKey(Long projectId, Long actorId) {
        Project project = findProject(projectId);
        // TODO(P7): 진행 중인 캠페인 확인 후 grace period 처리 — 역할 5 워커 연동
        String apiKey = generateApiKey();
        project.issueApiKey(sha256(apiKey));
        eventPublisher.publishEvent(AuditRecordedEvent.of(
                projectId, actorId, AuditAction.API_KEY_REVOKED, "Project", projectId,
                null, null, Map.of("keyPrefix", apiKey.substring(0, 12) + "...")
        ));
        return apiKey;
    }

    private String generateApiKey() {
        return "pk_live_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    // DB에는 해시만 저장 — SDK 인증 시 sha256(incomingKey)로 findByPublicApiKey() 호출
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 해싱 실패", e);
        }
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(TenantErrorCode.PROJECT_NOT_FOUND));
    }
}
