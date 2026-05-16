package com.testforge.runner.report.sections;

import com.testforge.ai.analysis.FailureAnalysisResult;
import com.testforge.runner.model.ExecutionReport;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FailureAnalysisSection implements ReportSection {

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

        // Category breakdown
        Map<String, Long> categoryBreakdown = analyses.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getRootCauseCategory() != null ? a.getRootCauseCategory() : "UNCERTAIN",
                        Collectors.counting()));

        sb.append("<div class=\"cards\">\n");
        for (Map.Entry<String, Long> e : categoryBreakdown.entrySet()) {
            sb.append("<div class=\"card\"><div class=\"label\">")
              .append(HtmlUtil.esc(e.getKey().replace("_", " ")))
              .append("</div><div class=\"value\" style=\"font-size:1.5rem;\">")
              .append(e.getValue()).append("</div></div>\n");
        }
        sb.append("</div>\n");

        // Each analysis as collapsible details
        for (FailureAnalysisResult a : analyses) {
            String category = a.getRootCauseCategory() != null ? a.getRootCauseCategory() : "UNCERTAIN";
            String confidence = a.getConfidence() != null ? a.getConfidence() : "LOW";
            String categoryLabel = category.replace("_", " ");

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

        return sb.toString();
    }
}
