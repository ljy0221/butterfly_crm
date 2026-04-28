package com.b101.butterfly.campaign.messagetemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateVariableExtractor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessageTemplateVariableExtractorTest {

    @Test
    @DisplayName("null 본문은 빈 리스트를 반환한다")
    void nullBodyReturnsEmpty() {
        assertThat(MessageTemplateVariableExtractor.extract(null)).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열/공백 문자열은 빈 리스트를 반환한다")
    void blankBodyReturnsEmpty() {
        assertThat(MessageTemplateVariableExtractor.extract("")).isEmpty();
        assertThat(MessageTemplateVariableExtractor.extract("   ")).isEmpty();
    }

    @Test
    @DisplayName("변수가 없는 본문은 빈 리스트를 반환한다")
    void bodyWithoutVariableReturnsEmpty() {
        assertThat(MessageTemplateVariableExtractor.extract("안녕하세요, 환영합니다."))
                .isEmpty();
    }

    @Test
    @DisplayName("단일 변수가 정상적으로 추출된다")
    void singleVariableExtracted() {
        assertThat(MessageTemplateVariableExtractor.extract("안녕하세요 {{ userName }}님"))
                .containsExactly("userName");
    }

    @Test
    @DisplayName("다중 변수는 등장 순서대로 추출된다")
    void multipleVariablesPreserveOrder() {
        assertThat(MessageTemplateVariableExtractor.extract("{{ a }} {{ b }} {{ c }}"))
                .containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("중복 변수는 제거되고 순서는 유지된다")
    void duplicateVariablesAreDeduplicated() {
        assertThat(MessageTemplateVariableExtractor.extract(
                "{{ name }} - {{ code }} - {{ name }}"))
                .containsExactly("name", "code");
    }

    @Test
    @DisplayName("중괄호 내부의 공백 변형(공백 없음/다중 공백)도 정상 매칭된다")
    void whitespaceVariantsMatch() {
        assertThat(MessageTemplateVariableExtractor.extract("{{name}}"))
                .containsExactly("name");
        assertThat(MessageTemplateVariableExtractor.extract("{{   name   }}"))
                .containsExactly("name");
    }

    @Test
    @DisplayName("언더스코어와 숫자가 포함된 식별자는 추출된다")
    void identifierWithUnderscoreAndDigits() {
        assertThat(MessageTemplateVariableExtractor.extract("{{ user_id_2 }}"))
                .containsExactly("user_id_2");
    }

    @Test
    @DisplayName("숫자로 시작하는 잘못된 식별자는 추출되지 않는다")
    void invalidIdentifierStartingWithDigitIgnored() {
        assertThat(MessageTemplateVariableExtractor.extract("{{ 2name }}")).isEmpty();
    }

    @Test
    @DisplayName("닫히지 않은 중괄호는 매칭되지 않는다")
    void unclosedBraceIgnored() {
        assertThat(MessageTemplateVariableExtractor.extract("{{ name")).isEmpty();
    }

    @Test
    @DisplayName("반환된 리스트는 불변(immutable)이다")
    void returnedListIsImmutable() {
        List<String> variables =
                MessageTemplateVariableExtractor.extract("{{ name }} {{ code }}");

        assertThatThrownBy(() -> variables.add("foo"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
