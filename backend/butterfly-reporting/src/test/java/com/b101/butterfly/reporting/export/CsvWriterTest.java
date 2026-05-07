package com.b101.butterfly.reporting.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CsvWriter — RFC 4180 직렬화")
class CsvWriterTest {

    @Nested
    @DisplayName("escape")
    class Escape {

        @Test
        @DisplayName("쉼표·따옴표·줄바꿈 없으면 원문 그대로")
        void plainValuesUnchanged() {
            assertThat(CsvWriter.escape("hello")).isEqualTo("hello");
            assertThat(CsvWriter.escape("123")).isEqualTo("123");
            assertThat(CsvWriter.escape("")).isEqualTo("");
        }

        @Test
        @DisplayName("쉼표 포함 — 따옴표로 감싸기")
        void commaQuoted() {
            assertThat(CsvWriter.escape("a,b")).isEqualTo("\"a,b\"");
        }

        @Test
        @DisplayName("따옴표 포함 — 두 번 연속 (\"\")으로 escape하고 전체 감싸기")
        void quoteEscapedDouble() {
            assertThat(CsvWriter.escape("a\"b")).isEqualTo("\"a\"\"b\"");
        }

        @Test
        @DisplayName("LF 포함 — 따옴표로 감싸기")
        void newlineQuoted() {
            assertThat(CsvWriter.escape("a\nb")).isEqualTo("\"a\nb\"");
        }

        @Test
        @DisplayName("CR 포함 — 따옴표로 감싸기")
        void carriageReturnQuoted() {
            assertThat(CsvWriter.escape("a\rb")).isEqualTo("\"a\rb\"");
        }

        @Test
        @DisplayName("null — 빈 문자열")
        void nullToEmpty() {
            assertThat(CsvWriter.escape(null)).isEqualTo("");
        }

        @Test
        @DisplayName("복합 — 쉼표 + 따옴표 + 줄바꿈 모두 포함")
        void allSpecials() {
            assertThat(CsvWriter.escape("a,b\"c\nd"))
                    .isEqualTo("\"a,b\"\"c\nd\"");
        }
    }

    @Nested
    @DisplayName("writeRow / writeHeader")
    class WriteRow {

        @Test
        @DisplayName("정상 row — 쉼표로 구분 + LF 종료")
        void simpleRow() throws IOException {
            StringWriter out = new StringWriter();
            CsvWriter csv = new CsvWriter(out);
            csv.writeRow(Arrays.asList("a", "b", "c"));
            assertThat(out.toString()).isEqualTo("a,b,c\n");
        }

        @Test
        @DisplayName("이스케이프 필요 row — RFC 4180 처리 후 출력")
        void escapesAreApplied() throws IOException {
            StringWriter out = new StringWriter();
            CsvWriter csv = new CsvWriter(out);
            csv.writeRow(Arrays.asList("plain", "with,comma", "with\"quote"));
            assertThat(out.toString()).isEqualTo("plain,\"with,comma\",\"with\"\"quote\"\n");
        }

        @Test
        @DisplayName("null cell — 빈 문자열로 출력")
        void nullCellsAreEmpty() throws IOException {
            StringWriter out = new StringWriter();
            CsvWriter csv = new CsvWriter(out);
            csv.writeRow(Arrays.asList("a", null, "c"));
            assertThat(out.toString()).isEqualTo("a,,c\n");
        }

        @Test
        @DisplayName("헤더 + 두 row — 줄바꿈으로 분리")
        void headerAndRows() throws IOException {
            StringWriter out = new StringWriter();
            CsvWriter csv = new CsvWriter(out);
            csv.writeHeader(List.of("id", "name"));
            csv.writeRow(List.of("1", "Alice"));
            csv.writeRow(List.of("2", "Bob"));
            assertThat(out.toString()).isEqualTo("id,name\n1,Alice\n2,Bob\n");
        }
    }
}
