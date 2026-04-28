package com.b101.butterfly.campaign.messagetemplate.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageTemplateVariableExtractor {

    // {{ userName }}
    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}");

    private MessageTemplateVariableExtractor() {
    }

    public static List<String> extract(String messageTemplateBody) {
        if (messageTemplateBody == null || messageTemplateBody.isBlank()) {
            return List.of();
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(messageTemplateBody);
        Set<String> variables = new LinkedHashSet<>();
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return List.copyOf(variables);
    }
}
