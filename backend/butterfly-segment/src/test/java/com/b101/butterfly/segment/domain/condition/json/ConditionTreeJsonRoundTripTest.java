package com.b101.butterfly.segment.domain.condition.json;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionTreeJsonRoundTripTest {

    private final ConditionTreeJsonConverter converter = new ConditionTreeJsonConverter();
    private final ObjectMapper compareMapper = new ObjectMapper();

    @ParameterizedTest(name = "round-trip: {0}")
    @ValueSource(
            strings = {
                "condition-tree/simple-tag.json",
                "condition-tree/event-with-period.json",
                "condition-tree/nested-and-or.json",
                "condition-tree/all-types.json"
            })
    @DisplayName("픽스처 4종 round-trip이 의미적으로 동일하다")
    void roundTrip(String resourcePath) throws IOException {
        String input = readResource(resourcePath);

        GroupNode parsed = converter.convertToEntityAttribute(input);
        assertThat(parsed).isNotNull();

        String output = converter.convertToDatabaseColumn(parsed);
        assertThat(output).isNotNull();

        JsonNode expected = compareMapper.readTree(input);
        JsonNode actual = compareMapper.readTree(output);
        assertThat(actual).isEqualTo(expected);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in =
                Objects.requireNonNull(
                        ConditionTreeJsonRoundTripTest.class
                                .getClassLoader()
                                .getResourceAsStream(path),
                        "Resource not found: " + path)) {
            return new String(in.readAllBytes());
        }
    }
}
