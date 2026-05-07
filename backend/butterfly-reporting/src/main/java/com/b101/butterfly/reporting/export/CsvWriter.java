package com.b101.butterfly.reporting.export;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * RFC 4180 기반 CSV 인쇄기 (S14P31B101-91 공용 컴포넌트).
 *
 * <p>현재는 reporting 모듈 안에 둔다 — 본 PR이 첫 사용처(audit 로그). 다른 도메인이
 * 재사용하기 시작하면 {@code butterfly-common}으로 승격할 것.
 *
 * <p><b>이스케이프 규칙</b> (RFC 4180 §2.6/§2.7):
 * <ol>
 *     <li>값에 쉼표(,), 큰따옴표("), 줄바꿈(CR/LF)이 들어 있으면 전체 값을 큰따옴표로 감싼다.</li>
 *     <li>값 안의 큰따옴표는 두 개("")로 두 번 입력해 escape한다.</li>
 *     <li>그 외 문자는 그대로 출력한다.</li>
 * </ol>
 *
 * <p><b>줄바꿈</b>: RFC 4180은 CRLF를 권장하지만 본 구현은 {@code \n}만 사용한다 — Excel 등
 * 주요 도구가 LF도 정상 인식하고 BOM과 결합 시 호환성이 더 일관적.
 *
 * <p><b>BOM</b>: 본 클래스는 BOM을 출력하지 않는다. 호출자가 응답 헤더 또는 파일 시작 시점에
 * UTF-8 BOM(﻿)을 직접 쓴다 — Excel에서 한글을 정상 인식하기 위함.
 *
 * <p>본 클래스는 thread-safe하지 않다. 한 요청 = 한 인스턴스 권장.
 */
public final class CsvWriter {

    private static final String SEPARATOR = ",";
    private static final String LINE_TERMINATOR = "\n";

    private final Writer writer;

    public CsvWriter(Writer writer) {
        this.writer = writer;
    }

    /** 헤더 row 출력. {@link #writeRow}와 동일하지만 의도 명시용 별칭. */
    public void writeHeader(List<String> headers) throws IOException {
        writeRow(headers);
    }

    /** 데이터 row 출력. null 값은 빈 문자열로 직렬화. */
    public void writeRow(List<String> cells) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(SEPARATOR);
            }
            line.append(escape(cells.get(i)));
        }
        line.append(LINE_TERMINATOR);
        writer.write(line.toString());
    }

    /**
     * RFC 4180 이스케이프. {@code null}은 빈 문자열로 직렬화하며, 따옴표가 필요한 케이스만
     * 감싸서 출력 크기를 최소화한다.
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuote) {
            return value;
        }
        // 큰따옴표는 ""로 escape하고 전체를 큰따옴표로 감싼다.
        StringBuilder sb = new StringBuilder(value.length() + 4);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                sb.append('"').append('"');
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public void flush() throws IOException {
        writer.flush();
    }
}
