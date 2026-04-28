package com.b101.butterfly.campaign.messagetemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.campaign.messagetemplate.domain.converter.RequiredVariablesConverter;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RequiredVariablesConverterTest {

    private final RequiredVariablesConverter converter = new RequiredVariablesConverter();

    @Nested
    @DisplayName("convertToDatabaseColumn() - 직렬화")
    class Serialize {

        @Test
        @DisplayName("null 입력은 빈 JSON 배열 문자열로 직렬화된다")
        void nullSerializesToEmptyArray() {
            assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
        }

        @Test
        @DisplayName("빈 리스트는 빈 JSON 배열 문자열로 직렬화된다")
        void emptyListSerializesToEmptyArray() {
            assertThat(converter.convertToDatabaseColumn(List.of())).isEqualTo("[]");
        }

        @Test
        @DisplayName("문자열 리스트는 JSON 배열 문자열로 직렬화된다")
        void listSerializesToJsonArray() {
            assertThat(converter.convertToDatabaseColumn(List.of("userName", "couponCode")))
                    .isEqualTo("[\"userName\",\"couponCode\"]");
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute() - 역직렬화")
    class Deserialize {

        @Test
        @DisplayName("null 입력은 빈 리스트로 역직렬화된다")
        void nullDeserializesToEmptyList() {
            assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        }

        @Test
        @DisplayName("빈/공백 문자열은 빈 리스트로 역직렬화된다")
        void blankDeserializesToEmptyList() {
            assertThat(converter.convertToEntityAttribute("")).isEmpty();
            assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
        }

        @Test
        @DisplayName("빈 JSON 배열 문자열은 빈 리스트로 역직렬화된다")
        void emptyJsonArrayDeserializesToEmptyList() {
            assertThat(converter.convertToEntityAttribute("[]")).isEmpty();
        }

        @Test
        @DisplayName("JSON 배열 문자열은 문자열 리스트로 역직렬화된다")
        void jsonArrayDeserializesToList() {
            assertThat(converter.convertToEntityAttribute("[\"a\",\"b\"]"))
                    .containsExactly("a", "b");
        }

        @Test
        @DisplayName("잘못된 JSON 문자열은 TEMPLATE_REQUIRED_VARIABLES_INVALID 예외로 래핑된다")
        void invalidJsonThrowsBusinessException() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("not-a-json"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MessageTemplateErrorCode.TEMPLATE_REQUIRED_VARIABLES_INVALID);
        }

        @Test
        @DisplayName("List가 아닌 JSON 객체는 TEMPLATE_REQUIRED_VARIABLES_INVALID 예외로 래핑된다")
        void nonListJsonThrowsBusinessException() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("{\"key\":\"value\"}"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MessageTemplateErrorCode.TEMPLATE_REQUIRED_VARIABLES_INVALID);
        }

        @Test
        @DisplayName("역직렬화 결과는 불변(immutable) 리스트이다")
        void deserializedListIsImmutable() {
            List<String> result = converter.convertToEntityAttribute("[\"a\",\"b\"]");

            assertThatThrownBy(() -> result.add("c"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Round-trip")
    class RoundTrip {

        @Test
        @DisplayName("리스트 → JSON → 리스트 round-trip 시 동일한 값을 유지한다")
        void roundTripPreservesValues() {
            List<String> original = List.of("a", "b", "c");

            String json = converter.convertToDatabaseColumn(original);
            List<String> restored = converter.convertToEntityAttribute(json);

            assertThat(restored).containsExactlyElementsOf(original);
        }
    }
}
