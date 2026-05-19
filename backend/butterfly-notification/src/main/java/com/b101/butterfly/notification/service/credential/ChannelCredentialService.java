package com.b101.butterfly.notification.service.credential;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.ChannelCredential;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.Channel;
import com.b101.butterfly.notification.entity.ChannelCredentialEntity;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.b101.butterfly.notification.repository.ChannelCredentialRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelCredentialService {

    private final ChannelCredentialRepository channelCredentialRepository;
    private final ObjectMapper objectMapper;

    public ChannelCredential getCredential(Long projectId, Channel channel) {
        ChannelCredentialEntity entity = channelCredentialRepository
                .findByProjectIdAndChannelAndActiveTrue(projectId, channel)
                .orElseThrow(() -> {
                    log.warn("채널 인증정보 없음: projectId={}, channel={}", projectId, channel);
                    return new BusinessException(DeliveryErrorCode.CREDENTIAL_NOT_FOUND);
                });

        log.info("채널 인증정보 조회 완료: projectId={}, channel={}", projectId, channel);
        return parseCredential(channel, entity.getCredentialData());
    }

    @Transactional
    public void saveCredential(Long projectId, Channel channel, String credentialData) {
        validateJson(credentialData);

        channelCredentialRepository.findByProjectIdAndChannel(projectId, channel)
                .ifPresentOrElse(
                        entity -> entity.updateCredentialData(credentialData),
                        () -> channelCredentialRepository.save(
                                new ChannelCredentialEntity(projectId, channel, credentialData))
                );

        log.info("채널 인증정보 저장: projectId={}, channel={}", projectId, channel);
    }

    @Transactional
    public void deleteCredential(Long projectId, Channel channel) {
        ChannelCredentialEntity entity = channelCredentialRepository
                .findByProjectIdAndChannel(projectId, channel)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.CREDENTIAL_NOT_FOUND));

        entity.deactivate();
        log.info("채널 인증정보 비활성화: projectId={}, channel={}", projectId, channel);
    }

    public String getCredentialData(Long projectId, Channel channel) {
        // 프론트 조회용: 등록 여부만 확인하므로 복호화 없이 마스킹 반환
        if (!channelCredentialRepository.findByProjectIdAndChannelAndActiveTrue(projectId, channel).isPresent()) {
            throw new BusinessException(DeliveryErrorCode.CREDENTIAL_NOT_FOUND);
        }
        return "***";
    }

    private ChannelCredential parseCredential(Channel channel, String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return switch (channel) {
                case PUSH -> new DeliveryCommand.FcmCredential(json);
                case SMS -> new DeliveryCommand.SmsCredential(
                        node.get("apiKey").asText(),
                        node.get("apiSecret").asText(),
                        node.get("senderNumber").asText());
                case WEBHOOK -> {
                    DeliveryCommand.WebhookFormat format = node.has("format")
                            ? DeliveryCommand.WebhookFormat.valueOf(node.get("format").asText())
                            : DeliveryCommand.WebhookFormat.DEFAULT;
                    yield new DeliveryCommand.WebhookCredential(
                            node.get("url").asText(),
                            node.has("secret") ? node.get("secret").asText() : null,
                            format);
                }
                case EMAIL, KAKAO -> new DeliveryCommand.NoCredential();
            };
        } catch (JsonProcessingException e) {
            throw new BusinessException(DeliveryErrorCode.CREDENTIAL_NOT_FOUND,
                    "인증정보 파싱 실패: " + e.getMessage());
        }
    }

    private void validateJson(String json) {
        try {
            objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BusinessException(DeliveryErrorCode.CREDENTIAL_NOT_FOUND,
                    "유효하지 않은 JSON 형식입니다.");
        }
    }
}
