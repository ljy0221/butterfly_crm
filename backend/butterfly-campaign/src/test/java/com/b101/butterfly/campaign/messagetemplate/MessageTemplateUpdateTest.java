package com.b101.butterfly.campaign.messagetemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MessageTemplate.update()")
class MessageTemplateUpdateTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    private MessageTemplate sample() {
        return MessageTemplate.create(
                PROJECT_ID,
                MEMBER_ID,
                "원본 템플릿",
                "원본 설명",
                Channel.EMAIL,
                "원본 제목",
                "원본 바디 {{ name }}",
                "https://image/before.png"
        );
    }

    @Nested
    @DisplayName("정상 경로")
    class HappyPath {

        @Test
        @DisplayName("모든 필드가 새 값으로 갱신된다")
        void allFieldsAreReplaced() {
            MessageTemplate template = sample();

            template.update(
                    "새 이름",
                    "새 설명",
                    Channel.SMS,
                    "새 제목",
                    "새 바디 {{ couponCode }}",
                    "https://image/after.png"
            );

            assertThat(template.getMessageTemplateName()).isEqualTo("새 이름");
            assertThat(template.getMessageTemplateDescription()).isEqualTo("새 설명");
            assertThat(template.getChannel()).isEqualTo(Channel.SMS);
            assertThat(template.getMessageTemplateTitle()).isEqualTo("새 제목");
            assertThat(template.getMessageTemplateBody()).isEqualTo("새 바디 {{ couponCode }}");
            assertThat(template.getMessageTemplateImageUrl()).isEqualTo("https://image/after.png");
        }

        @Test
        @DisplayName("바디가 바뀌면 requiredVariables도 재추출된다")
        void requiredVariablesAreReExtracted() {
            MessageTemplate template = sample();

            template.update(
                    "새 이름",
                    "새 설명",
                    Channel.EMAIL,
                    "새 제목",
                    "안녕하세요 {{ userName }}님, 쿠폰코드 {{ couponCode }}",
                    "https://image/x.png"
            );

            assertThat(template.getRequiredVariables())
                    .containsExactly("userName", "couponCode");
        }
    }

    @Nested
    @DisplayName("이미지 URL 정규화")
    class ImageNormalization {

        @Test
        @DisplayName("null 이미지 URL은 제거된 것으로 처리된다")
        void nullImageBecomesNull() {
            MessageTemplate template = sample();

            template.update("이름", "설명", Channel.EMAIL, "제목", "바디", null);

            assertThat(template.getMessageTemplateImageUrl()).isNull();
        }

        @Test
        @DisplayName("빈 문자열 이미지 URL은 제거된 것으로 처리된다")
        void emptyImageBecomesNull() {
            MessageTemplate template = sample();

            template.update("이름", "설명", Channel.EMAIL, "제목", "바디", "");

            assertThat(template.getMessageTemplateImageUrl()).isNull();
        }

        @Test
        @DisplayName("공백만 있는 이미지 URL은 제거된 것으로 처리된다")
        void blankImageBecomesNull() {
            MessageTemplate template = sample();

            template.update("이름", "설명", Channel.EMAIL, "제목", "바디", "   ");

            assertThat(template.getMessageTemplateImageUrl()).isNull();
        }

        @Test
        @DisplayName("정상 URL은 그대로 유지된다")
        void validUrlIsKept() {
            MessageTemplate template = sample();

            template.update("이름", "설명", Channel.EMAIL, "제목", "바디",
                    "https://cdn/path.png");

            assertThat(template.getMessageTemplateImageUrl())
                    .isEqualTo("https://cdn/path.png");
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class Validation {

        @Test
        @DisplayName("channel이 null이면 TEMPLATE_CHANNEL_REQUIRED 예외")
        void nullChannelRejected() {
            MessageTemplate template = sample();

            assertThatThrownBy(() -> template.update(
                    "이름", "설명", null, "제목", "바디", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MessageTemplateErrorCode.TEMPLATE_CHANNEL_REQUIRED);
        }

        @Test
        @DisplayName("이름이 비어 있으면 TEMPLATE_NAME_REQUIRED 예외")
        void blankNameRejected() {
            MessageTemplate template = sample();

            assertThatThrownBy(() -> template.update(
                    "  ", "설명", Channel.EMAIL, "제목", "바디", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MessageTemplateErrorCode.TEMPLATE_NAME_REQUIRED);
        }

        @Test
        @DisplayName("설명이 비어 있으면 TEMPLATE_DESCRIPTION_REQUIRED 예외")
        void blankDescriptionRejected() {
            MessageTemplate template = sample();

            assertThatThrownBy(() -> template.update(
                    "이름", "", Channel.EMAIL, "제목", "바디", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MessageTemplateErrorCode.TEMPLATE_DESCRIPTION_REQUIRED);
        }

        @Test
        @DisplayName("제목이 비어 있으면 TEMPLATE_TITLE_REQUIRED 예외")
        void blankTitleRejected() {
            MessageTemplate template = sample();

            assertThatThrownBy(() -> template.update(
                    "이름", "설명", Channel.EMAIL, " ", "바디", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MessageTemplateErrorCode.TEMPLATE_TITLE_REQUIRED);
        }

        @Test
        @DisplayName("바디가 비어 있으면 TEMPLATE_BODY_REQUIRED 예외")
        void blankBodyRejected() {
            MessageTemplate template = sample();

            assertThatThrownBy(() -> template.update(
                    "이름", "설명", Channel.EMAIL, "제목", "", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MessageTemplateErrorCode.TEMPLATE_BODY_REQUIRED);
        }
    }

    @Nested
    @DisplayName("isOwnedBy()")
    class Ownership {

        @Test
        @DisplayName("같은 projectId면 true")
        void sameProjectReturnsTrue() {
            MessageTemplate template = sample();

            assertThat(template.isOwnedBy(PROJECT_ID)).isTrue();
        }

        @Test
        @DisplayName("다른 projectId면 false")
        void differentProjectReturnsFalse() {
            MessageTemplate template = sample();

            assertThat(template.isOwnedBy(999L)).isFalse();
        }
    }
}
