package com.b101.butterfly.campaign.messagetemplate.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 메시지 본문/제목/이미지 URL 안의 {@code {{var}}} 변수를 사용자별 값으로 치환한다.
 *
 * <p>{@link MessageTemplateVariableExtractor} 와 동일한 {@link #VARIABLE_PATTERN} 사용 — 변수 추출과
 * 변수 치환이 같은 규칙을 따른다.
 *
 * <p>Fallback 정책 (S14P31B101-289 도메인 결정):
 * <ul>
 *   <li>변수 값이 없거나 {@code null}: <b>빈 문자열</b>로 치환 — 사용자에게 raw placeholder({@code {{name}}})
 *       노출 안 함 (제품 신뢰도 보호).</li>
 *   <li>그 시점에 운영자가 알아채도록 <b>WARN 로그</b>를 함께 남긴다.</li>
 * </ul>
 *
 * <p>스레드 안전 — 인스턴스 상태 없음 (static utility).
 */
public final class VariableSubstitutor {

    private static final Logger log = LoggerFactory.getLogger(VariableSubstitutor.class);

    // {{ userName }} — MessageTemplateVariableExtractor 와 동일 패턴
    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}");

    private VariableSubstitutor() {
    }

    /**
     * 템플릿 문자열에 {@code {{var}}} 패턴이 하나라도 있는지 검사. FCM 의 multicast/sendEach 분기처럼
     * "변수가 있는 경우만 per-target 분할" 의 빠른 가드로 쓰인다.
     */
    public static boolean hasVariablePattern(String template) {
        if (template == null || template.isEmpty()) {
            return false;
        }
        return VARIABLE_PATTERN.matcher(template).find();
    }

    /**
     * 템플릿 문자열 안의 {@code {{var}}} 를 {@code variables} 에서 찾아 치환한다.
     *
     * <p>입력이 비어있거나 변수가 하나도 매치되지 않으면 입력 그대로 반환. 치환에 사용된 키만 매핑을
     * 조회하므로 variables 가 큰 맵이어도 비용은 매치 개수에 비례.
     *
     * @param template  치환 대상 문자열 (null/빈 문자열이면 그대로 반환)
     * @param variables {@code tagKey → tagValue} 맵 (null 이면 빈 맵으로 취급)
     * @return 치환된 문자열
     */
    public static String substitute(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Map<String, String> safeVariables = variables != null ? variables : Map.of();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = safeVariables.get(key);
            if (value == null) {
                log.warn("개인화 변수 값 누락 — key={} (빈 문자열로 치환)", key);
                value = "";
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
