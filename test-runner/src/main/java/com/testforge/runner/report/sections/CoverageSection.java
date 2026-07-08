package com.testforge.runner.report.sections;

import com.testforge.runner.model.CoverageMetric;
import com.testforge.runner.model.CoverageStatus;
import com.testforge.runner.model.CoverageSummary;
import com.testforge.runner.model.ExecutionReport;

public class CoverageSection implements ReportSection {

    @Override
    public String render(ExecutionReport report) {
        CoverageSummary coverage = report.getCoverage();
        if (coverage == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<p class=\"meta\">Target API Coverage &middot; Module: ")
                .append(HtmlUtil.esc(coverage.getTargetModule()))
                .append("</p>\n");

        if (coverage.getStatus() != CoverageStatus.AVAILABLE) {
            sb.append("<div class=\"coverage-state coverage-")
                    .append(coverage.getStatus())
                    .append("\">")
                    .append(HtmlUtil.esc(coverage.getMessage()))
                    .append("</div>\n");
            return sb.toString();
        }

        sb.append("<table class=\"coverage-table\"><thead><tr>")
                .append("<th class=\"metric-col\">Metric</th>")
                .append("<th class=\"number-col\">Covered</th>")
                .append("<th class=\"number-col\">Missed</th>")
                .append("<th class=\"number-col\">Total</th>")
                .append("<th class=\"number-col\">Coverage</th>")
                .append("</tr></thead><tbody>\n");
        appendMetric(sb, "Lines", coverage.getLine());
        appendMetric(sb, "Branches", coverage.getBranch());
        appendMetric(sb, "Instructions", coverage.getInstruction());
        appendMetric(sb, "Methods", coverage.getMethod());
        appendMetric(sb, "Classes", coverage.getClazz());
        appendMetric(sb, "Complexity", coverage.getComplexity());
        sb.append("</tbody></table>\n");

        if (!isBlank(coverage.getDetailsPath())) {
            sb.append("<p><a class=\"coverage-link action-link\" href=\"")
                    .append(HtmlUtil.esc(coverage.getDetailsPath()))
                    .append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                    .append("View JaCoCo Details &rarr;</a></p>\n");
        }
        return sb.toString();
    }

    @Override
    public String getSectionId() {
        return "coverage";
    }

    @Override
    public String getNavLabel() {
        return "Coverage";
    }

    @Override
    public String getIcon() {
        return "📈";
    }

    @Override
    public boolean hasContent(ExecutionReport report) {
        return report.getCoverage() != null;
    }

    private void appendMetric(StringBuilder sb, String label, CoverageMetric metric) {
        sb.append("<tr><td class=\"metric-col\">").append(label).append("</td>");
        if (metric == null) {
            sb.append("<td class=\"number-col\">-</td>")
                    .append("<td class=\"number-col\">-</td>")
                    .append("<td class=\"number-col\">-</td>")
                    .append("<td class=\"number-col\">-</td>");
        } else {
            sb.append("<td class=\"number-col\">").append(metric.getCovered()).append("</td>")
                    .append("<td class=\"number-col\">").append(metric.getMissed()).append("</td>")
                    .append("<td class=\"number-col\">").append(metric.getTotal()).append("</td>")
                    .append("<td class=\"number-col\">").append(formatPercentage(metric)).append("</td>");
        }
        sb.append("</tr>\n");
    }

    private String formatPercentage(CoverageMetric metric) {
        return String.format("%.1f%%", metric.getPercentage());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
