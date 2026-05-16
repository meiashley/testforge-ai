package com.testforge.runner.execution;

import com.testforge.ai.scenario.StepDataContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BindingResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    public String resolve(String template, StepDataContext context) {
        if (template == null) return null;
        Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object val = context.get(key);
            String replacement = val != null ? String.valueOf(val) : m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public Map<String, String> resolveMap(Map<String, String> map, StepDataContext context) {
        if (map == null) return Map.of();
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            resolved.put(entry.getKey(), resolve(entry.getValue(), context));
        }
        return resolved;
    }
}
