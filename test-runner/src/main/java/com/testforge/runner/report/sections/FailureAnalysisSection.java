package com.testforge.runner.report.sections;

import com.testforge.ai.analysis.FailureAnalysisResult;
import com.testforge.runner.model.ExecutionReport;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FailureAnalysisSection implements ReportSection {

    private static final Comparator<String> CATEGORY_ORDER =
            Comparator.comparingInt(FailureAnalysisSection::categoryRank)
                    .thenComparing(FailureAnalysisSection::safe);

    private static final Comparator<FailureAnalysisResult> ANALYSIS_ORDER =
            Comparator.comparing((FailureAnalysisResult a) -> normalizedCategory(a), CATEGORY_ORDER)
                    .thenComparing((FailureAnalysisResult a) -> confidenceRank(a.getConfidence()))
                    .thenComparing(a -> safe(a.getTestCaseName()));

    @Override
    public String getSectionId() { return "failure-analysis"; }

    @Override
    public String getNavLabel() { return "AI Analysis"; }

    @Override
    public String getIcon() { return "🤖"; }

    @Override
    public boolean hasContent(ExecutionReport report) {
        List<FailureAnalysisResult> fa = report.getFailureAnalysis();
        return fa != null && !fa.isEmpty();
    }

    @Override
    public String render(ExecutionReport report) {
        List<FailureAnalysisResult> analyses = report.getFailureAnalysis();
        if (analyses == null || analyses.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        List<FailureAnalysisResult> sortedAnalyses = analyses.stream()
                .sorted(ANALYSIS_ORDER)
                .toList();

        // Category breakdown
        Map<String, Long> categoryBreakdown = sortedAnalyses.stream()
                .collect(Collectors.groupingBy(
                        FailureAnalysisSection::normalizedCategory,
                        LinkedHashMap::new,
                        Collectors.counting()));

        sb.append("<div class=\"cards\">\n");
        for (Map.Entry<String, Long> e : categoryBreakdown.entrySet()) {
            sb.append("<div class=\"card\"><div class=\"label\">")
              .append(HtmlUtil.esc(e.getKey().replace("_", " ")))
              .append("</div><div class=\"value\" style=\"font-size:1.5rem;\">")
              .append(e.getValue()).append("</div></div>\n");
        }
        sb.append("</div>\n");

        Map<String, List<FailureAnalysisResult>> groupedAnalyses = sortedAnalyses.stream()
                .collect(Collectors.groupingBy(
                        FailureAnalysisSection::normalizedCategory,
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<String, List<FailureAnalysisResult>> entry : groupedAnalyses.entrySet()) {
            String category = entry.getKey();
            String categoryLabel = category.replace("_", " ");

            sb.append("<h3>").append(HtmlUtil.esc(categoryLabel)).append("</h3>\n");

            // Each analysis as collapsible details within category group
            for (FailureAnalysisResult a : entry.getValue()) {
                String confidence = a.getConfidence() != null ? a.getConfidence() : "LOW";
                sb.append("<details>\n")
                  .append("<summary>")
                  .append("<span class=\"name\">").append(HtmlUtil.esc(a.getTestCaseName())).append("</span>")
                  .append("<span class=\"category-badge category-").append(HtmlUtil.esc(category)).append("\">")
                  .append(HtmlUtil.esc(categoryLabel)).append("</span>")
                  .append("&nbsp;<span class=\"confidence-badge confidence-").append(HtmlUtil.esc(confidence)).append("\">")
                  .append(HtmlUtil.esc(confidence)).append("</span>")
                  .append("</summary>\n")
                  .append("<div class=\"details-body\">\n");

                if (a.getRootCauseSummary() != null) {
                    sb.append("<div class=\"ai-field\"><strong>Root Cause:</strong> ")
                      .append(HtmlUtil.esc(a.getRootCauseSummary())).append("</div>\n");
                }
                if (a.getEvidence() != null) {
                    sb.append("<div class=\"ai-field\"><strong>Evidence:</strong> ")
                      .append(HtmlUtil.esc(a.getEvidence())).append("</div>\n");
                }
                if (a.getSuggestedFix() != null) {
                    sb.append("<div class=\"ai-field\"><strong>Suggested Fix:</strong> ")
                      .append(HtmlUtil.esc(a.getSuggestedFix())).append("</div>\n");
                }

                sb.append("</div>\n</details>\n");
            }
        }

        return sb.toString();
    }

    private static String normalizedCategory(FailureAnalysisResult analysis) {
        return analysis.getRootCauseCategory() != null ? analysis.getRootCauseCategory() : "UNCERTAIN";
    }

    private static int categoryRank(String category) {
        return switch (safe(category)) {
            case "API_BUG" -> 0;
            case "TEST_LOGIC_ERROR" -> 1;
            case "ASSERTION_TOO_STRICT" -> 2;
            case "DATA_DEPENDENCY" -> 3;
            case "ENVIRONMENT" -> 4;
            case "UNCERTAIN" -> 5;
            default -> 6;
        };
    }

    private static int confidenceRank(String confidence) {
        return switch (safe(confidence)) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            case "LOW" -> 2;
            default -> 3;
        };
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
