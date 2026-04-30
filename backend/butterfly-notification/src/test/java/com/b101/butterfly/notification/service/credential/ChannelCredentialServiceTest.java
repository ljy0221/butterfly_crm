package com.b101.butterfly.notification.service.credential;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.Channel;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.ChannelCredential;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.FcmCredential;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.SmsCredential;
import com.b101.butterfly.notification.entity.ChannelCredentialEntity;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.b101.butterfly.notification.repository.ChannelCredentialRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelCredentialServiceTest {

    @Mock private ChannelCredentialRepository channelCredentialRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private ChannelCredentialService channelCredentialService;

    private static final Long PROJECT_ID = 1L;

    @Nested
    @DisplayName("getCredential()")
    class GetCredentialTests {

        @Test
        @DisplayName("PUSH 채널 활성 인증정보가 있으면 FcmCredential을 반환한다")
        void pushChannel_returnsFcmCredential() throws JsonProcessingException {
            String json = "{\"type\":\"service_account\"}";
            ChannelCredentialEntity entity = new ChannelCredentialEntity(PROJECT_ID, Channel.PUSH, json);

            when(channelCredentialRepository.findByProjectIdAndChannelAndActiveTrue(PROJECT_ID, Channel.PUSH))
                    .thenReturn(Optional.of(entity));
            when(objectMapper.readTree(json))
                    .thenReturn(new ObjectMapper().readTree(json));

            ChannelCredential credential = channelCredentialService.getCredential(PROJECT_ID, Channel.PUSH);

            assertThat(credential).isInstanceOf(FcmCredential.class);
            assertThat(((FcmCredential) credential).credentialJson()).isEqualTo(json);
        }

        @Test
        @DisplayName("SMS 채널 활성 인증정보가 있으면 SmsCredential을 반환한다")
        void smsChannel_returnsSmsCredential() throws JsonProcessingException {
            String json = "{\"apiKey\":\"key1\",\"apiSecret\":\"secret1\",\"senderNumber\":\"01012345678\"}";
            ChannelCredentialEntity entity = new ChannelCredentialEntity(PROJECT_ID, Channel.SMS, json);

            when(channelCredentialRepository.findByProjectIdAndChannelAndActiveTrue(PROJECT_ID, Channel.SMS))
                    .thenReturn(Optional.of(entity));
            when(objectMapper.readTree(json))
                    .thenReturn(new ObjectMapper().readTree(json));

            ChannelCredential credential = channelCredentialService.getCredential(PROJECT_ID, Channel.SMS);

            assertThat(credential).isInstanceOf(SmsCredential.class);
            SmsCredential sms = (SmsCredential) credential;
            assertThat(sms.apiKey()).isEqualTo("key1");
            assertThat(sms.apiSecret()).isEqualTo("secret1");
            assertThat(sms.senderNumber()).isEqualTo("01012345678");
        }

        @Test
        @DisplayName("인증정보가 없으면 CREDENTIAL_NOT_FOUND 예외를 던진다")
        void notFound_throwsException() {
            when(channelCredentialRepository.findByProjectIdAndChannelAndActiveTrue(PROJECT_ID, Channel.PUSH))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> channelCredentialService.getCredential(PROJECT_ID, Channel.PUSH))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(DeliveryErrorCode.CREDENTIAL_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("saveCredential()")
    class SaveCredentialTests {

        @Test
        @DisplayName("신규 인증정보를 저장한다")
        void newCredential_saves() throws JsonProcessingException {
            String json = "{\"type\":\"service_account\"}";

            when(objectMapper.readTree(json)).thenReturn(new ObjectMapper().readTree(json));
            when(channelCredentialRepository.findByProjectIdAndChannel(PROJECT_ID, Channel.PUSH))
                    .thenReturn(Optional.empty());

            channelCredentialService.saveCredential(PROJECT_ID, Channel.PUSH, json);

            verify(channelCredentialRepository).save(any(ChannelCredentialEntity.class));
        }

        @Test
        @DisplayName("기존 인증정보가 있으면 업데이트한다")
        void existingCredential_updates() throws JsonProcessingException {
            String oldJson = "{\"type\":\"old\"}";
            String newJson = "{\"type\":\"new\"}";
            ChannelCredentialEntity entity = new ChannelCredentialEntity(PROJECT_ID, Channel.PUSH, oldJson);

            when(objectMapper.readTree(newJson)).thenReturn(new ObjectMapper().readTree(newJson));
            when(channelCredentialRepository.findByProjectIdAndChannel(PROJECT_ID, Channel.PUSH))
                    .thenReturn(Optional.of(entity));

            channelCredentialService.saveCredential(PROJECT_ID, Channel.PUSH, newJson);

            assertThat(entity.getCredentialData()).isEqualTo(newJson);
            assertThat(entity.isActive()).isTrue();
        }

        @Test
        @DisplayName("유효하지 않은 JSON이면 예외를 던진다")
        void invalidJson_throwsException() throws JsonProcessingException {
            String invalidJson = "not-json";

            when(objectMapper.readTree(invalidJson)).thenThrow(new JsonProcessingException("invalid") {});

            assertThatThrownBy(() -> channelCredentialService.saveCredential(PROJECT_ID, Channel.PUSH, invalidJson))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(DeliveryErrorCode.CREDENTIAL_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("deleteCredential()")
    class DeleteCredentialTests {

        @Test
        @DisplayName("정상적으로 비활성화한다")
        void deactivatesCredential() {
            ChannelCredentialEntity entity = new ChannelCredentialEntity(PROJECT_ID, Channel.PUSH, "{\"type\":\"service_account\"}");

            when(channelCredentialRepository.findByProjectIdAndChannel(PROJECT_ID, Channel.PUSH))
                    .thenReturn(Optional.of(entity));

            channelCredentialService.deleteCredential(PROJECT_ID, Channel.PUSH);

            assertThat(entity.isActive()).isFalse();
        }
    }
}
