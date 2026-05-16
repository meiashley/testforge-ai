package com.testforge.runner.report.sections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

class HtmlUtil {

    private static final ObjectMapper PRETTY_JSON =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    static String esc(Object o) {
        if (o == null) return "";
        String s = o.toString();
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static String card(String label, String value, String extraClass) {
        return "<div class=\"card" + extraClass + "\"><div class=\"label\">" + label
                + "</div><div class=\"value\">" + value + "</div></div>\n";
    }

    static String toJson(Object obj) {
        try {
            return PRETTY_JSON.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
