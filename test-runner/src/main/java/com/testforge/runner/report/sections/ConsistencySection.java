package com.testforge.runner.report.sections;

import com.testforge.ai.consistency.AlignmentResult;
import com.testforge.ai.consistency.ConsistencyMismatch;
import com.testforge.runner.model.ExecutionReport;

import java.util.Map;

public class ConsistencySection implements ReportSection {

    @Override
    public String getSectionId() { return "consistency"; }

    @Override
    public String getNavLabel() { return "Consistency"; }

    @Override
    public String getIcon() { return "🔍"; }

    @Override
    public boolean hasContent(ExecutionReport report) {
        AlignmentResult r = report.getConsistencyResult();
        return r != null && r.getMismatches() != null && !r.getMismatches().isEmpty();
    }

    @Override
    public String render(ExecutionReport report) {
        AlignmentResult result = report.getConsistencyResult();
        if (result == null) return "";

        StringBuilder sb = new StringBuilder();

        // Breakdown table
        sb.append("<div class=\"cards\">\n");
        sb.append(HtmlUtil.card("Total Constraints", String.valueOf(result.getTotalConstraints()), ""));
        sb.append(HtmlUtil.card("Aligned Constraints", String.valueOf(result.getAlignedCount()), ""));
        sb.append("<div class=\"card\"><div class=\"label\">Constraints With Mismatch</div>"
                + "<div class=\"value\" style=\"color:" + (result.getConstraintsWithMismatchCount() > 0 ? "#dc2626" : "#16a34a") + "\">"
                + result.getConstraintsWithMismatchCount() + "</div></div>\n");
        sb.append("<div class=\"card\"><div class=\"label\">Mismatch Records</div>"
                + "<div class=\"value\" style=\"color:" + (result.getMismatchCount() > 0 ? "#dc2626" : "#16a34a") + "\">"
                + result.getMismatchCount() + "</div></div>\n");
        sb.append("</div>\n");

        // Severity breakdown
        if (result.getSeverityBreakdown() != null && !result.getSeverityBreakdown().isEmpty()) {
            sb.append("<div class=\"cards\">\n");
            for (Map.Entry<String, Integer> e : result.getSeverityBreakdown().entrySet()) {
                String color = severityColor(e.getKey());
                sb.append("<div class=\"card\"><div class=\"label\">").append(HtmlUtil.esc(e.getKey()))
                  .append("</div><div class=\"value\" style=\"color:").append(color).append("\">")
                  .append(e.getValue()).append("</div></div>\n");
            }
            sb.append("</div>\n");
        }

        // Each mismatch as collapsible details
        if (result.getMismatches() != null) {
            for (ConsistencyMismatch m : result.getMismatches()) {
                sb.append("<details>\n<summary>")
                  .append("<span class=\"severity-badge severity-").append(HtmlUtil.esc(m.getSeverity())).append("\">")
                  .append(HtmlUtil.esc(m.getSeverity())).append("</span>")
                  .append("<span class=\"category-mm-badge category-mm-").append(HtmlUtil.esc(m.getCategory())).append("\">")
                  .append(HtmlUtil.esc(m.getCategory())).append("</span>")
                  .append("<span class=\"name\">").append(HtmlUtil.esc(m.getSummary())).append("</span>")
                  .append("</summary>\n")
                  .append("<div class=\"details-body\">\n");
                field(sb, "Evidence", m.getEvidence());
                field(sb, "Location", m.getLocation());
                field(sb, "Requirement", m.getRequirementReference());
                field(sb, "Spec", m.getSpecReference());
                field(sb, "Confidence", m.getConfidence());
                sb.append("</div>\n</details>\n");
            }
        }

        return sb.toString();
    }

    private void field(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append("<div class=\"ai-field\"><strong>").append(label).append(":</strong> ")
          .append(HtmlUtil.esc(value)).append("</div>\n");
    }

    private String severityColor(String severity) {
        return switch (severity) {
            case "HIGH" -> "#dc2626";
            case "MEDIUM" -> "#f59e0b";
            default -> "#6b7280";
        };
    }
}
