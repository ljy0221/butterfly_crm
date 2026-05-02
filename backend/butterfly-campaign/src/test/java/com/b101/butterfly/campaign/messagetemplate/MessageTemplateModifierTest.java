package com.b101.butterfly.campaign.messagetemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
import com.b101.butterfly.campaign.campaign.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateModifier;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateRepository;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateUsageGuard;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MessageTemplateModifier")
class MessageTemplateModifierTest {

    private static final Long TEMPLATE_ID = 100L;
    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    private MessageTemplateRepository messageTemplateRepository;
    private MessageTemplateUsageGuard messageTemplateUsageGuard;
    private MessageTemplateModifier modifier;

    @BeforeEach
    void setUp() {
        messageTemplateRepository = mock(MessageTemplateRepository.class);
        messageTemplateUsageGuard = mock(MessageTemplateUsageGuard.class);
        modifier = new MessageTemplateModifier(messageTemplateRepository, messageTemplateUsageGuard);
    }

    @Nested
    @DisplayName("정상 경로")
    class HappyPath {

        @Test
        @DisplayName("영속 엔티티를 로드해 도메인 update를 호출하고 ID를 반환한다")
        void loadsPersistedEntityAndUpdates() {
            MessageTemplate persisted = persistedTemplate(TEMPLATE_ID, PROJECT_ID);
            given(messageTemplateRepository.getByMessageTemplateId(TEMPLATE_ID))
                    .willReturn(persisted);

            Long returnedId = modifier.updateMessageTemplate(
                    TEMPLATE_ID,
                    PROJECT_ID,
                    "새 이름",
                    "새 설명",
                    Channel.SMS,
                    "새 제목",
                    "새 바디 {{ couponCode }}",
                    "https://image/x.png"
            );

            assertThat(returnedId).isEqualTo(TEMPLATE_ID);
            assertThat(persisted.getMessageTemplateName()).isEqualTo("새 이름");
            assertThat(persisted.getChannel()).isEqualTo(Channel.SMS);
            assertThat(persisted.getMessageTemplateBody())
                    .isEqualTo("새 바디 {{ couponCode }}");
            assertThat(persisted.getRequiredVariables()).containsExactly("couponCode");
            verify(messageTemplateUsageGuard).assertNotInUseByActiveCampaign(TEMPLATE_ID);
        }
    }

    @Nested
    @DisplayName("실패 경로")
    class FailurePath {

        @Test
        @DisplayName("템플릿이 없으면 TEMPLATE_NOT_FOUND")
        void unknownTemplateThrowsNotFound() {
            given(messageTemplateRepository.getByMessageTemplateId(TEMPLATE_ID))
                    .willThrow(new BusinessException(MessageTemplateErrorCode.TEMPLATE_NOT_FOUND));

            assertThatThrownBy(() -> modifier.updateMessageTemplate(
                    TEMPLATE_ID, PROJECT_ID, "이름", "설명",
                    Channel.EMAIL, "제목", "바디", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MessageTemplateErrorCode.TEMPLATE_NOT_FOUND);

            verify(messageTemplateUsageGuard, never()).assertNotInUseByActiveCampaign(anyLong());
        }

        @Test
        @DisplayName("다른 프로젝트의 템플릿을 수정하려 하면 TEMPLATE_NOT_FOUND")
        void crossProjectUpdateRejected() {
            MessageTemplate persisted = persistedTemplate(TEMPLATE_ID, PROJECT_ID);
            given(messageTemplateRepository.getByMessageTemplateId(TEMPLATE_ID))
                    .willReturn(persisted);

            Long otherProjectId = 999L;
            assertThatThrownBy(() -> modifier.updateMessageTemplate(
                    TEMPLATE_ID, otherProjectId, "이름", "설명",
                    Channel.EMAIL, "제목", "바디", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MessageTemplateErrorCode.TEMPLATE_NOT_FOUND);

            verify(messageTemplateUsageGuard, never()).assertNotInUseByActiveCampaign(anyLong());
        }

        @Test
        @DisplayName("UsageGuard가 CAMPAIGN_IS_ACTIVE 던지면 그대로 전파한다")
        void activeCampaignBlocksUpdate() {
            MessageTemplate persisted = persistedTemplate(TEMPLATE_ID, PROJECT_ID);
            given(messageTemplateRepository.getByMessageTemplateId(TEMPLATE_ID))
                    .willReturn(persisted);
            doThrow(new BusinessException(CampaignErrorCode.CAMPAIGN_IS_ACTIVE))
                    .when(messageTemplateUsageGuard).assertNotInUseByActiveCampaign(TEMPLATE_ID);

            assertThatThrownBy(() -> modifier.updateMessageTemplate(
                    TEMPLATE_ID, PROJECT_ID, "이름", "설명",
                    Channel.EMAIL, "제목", "바디", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_IS_ACTIVE);
        }
    }

    private MessageTemplate persistedTemplate(Long templateId, Long projectId) {
        MessageTemplate template = MessageTemplate.create(
                projectId,
                MEMBER_ID,
                "원본 이름",
                "원본 설명",
                Channel.EMAIL,
                "원본 제목",
                "원본 바디",
                null
        );
        setField(template, "messageTemplateId", templateId);
        return template;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
