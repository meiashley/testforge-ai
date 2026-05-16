package com.testforge.runner.report.sections;

import com.testforge.runner.execution.AssertionResult;
import com.testforge.runner.execution.PlanExecutionResult;
import com.testforge.runner.execution.StepResult;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.HttpResponse;

import java.util.List;

public class ScenarioSection implements ReportSection {

    @Override
    public String getSectionId() { return "scenarios"; }

    @Override
    public String getNavLabel() { return "Scenarios"; }

    @Override
    public String getIcon() { return "🎬"; }

    @Override
    public boolean hasContent(ExecutionReport report) {
        List<PlanExecutionResult> s = report.getScenarioResults();
        return s != null && !s.isEmpty();
    }

    @Override
    public String render(ExecutionReport report) {
        List<PlanExecutionResult> scenarios = report.getScenarioResults();
        if (scenarios == null || scenarios.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (PlanExecutionResult plan : scenarios) {
            boolean passed = plan.isPassed();
            String icon = passed ? "✅" : "❌";
            int stepCount = plan.getSteps() != null ? plan.getSteps().size() : 0;

            sb.append("<details>\n<summary")
              .append(passed ? "" : " class=\"failed\"").append(">")
              .append("<span>").append(icon).append("</span>")
              .append("<span class=\"name\">").append(HtmlUtil.esc(plan.getScenarioName())).append("</span>")
              .append("<span class=\"meta-info\">").append(stepCount).append(" steps</span>")
              .append("</summary>\n<div class=\"details-body\">\n");

            if (plan.getSteps() != null) {
                for (StepResult sr : plan.getSteps()) {
                    renderStep(sb, sr);
                }
            }

            sb.append("</div>\n</details>\n");
        }
        return sb.toString();
    }

    private void renderStep(StringBuilder sb, StepResult sr) {
        boolean skipped = sr.getSkipReason() != null;
        boolean passed = sr.isPassed() && !skipped;
        String stepIcon = skipped ? "⏭" : passed ? "✅" : "❌";
        String method = sr.getStep() != null ? sr.getStep().getMethod() : "";
        String path = sr.getStep() != null ? sr.getStep().getPathTemplate() : "";
        String role = sr.getStep() != null ? sr.getStep().getRole() : "";
        int status = sr.getResponse() != null ? sr.getResponse().getStatusCode() : 0;
        String statusStr = sr.getResponse() != null ? "HTTP " + status : "—";

        sb.append("<details style=\"margin:8px 0; box-shadow:none; border:1px solid #e2e8f0;\">\n")
          .append("<summary style=\"background:").append(skipped ? "#f3f4f6" : passed ? "#f0fdf4" : "#fef2f2").append(";\">")
          .append("<span>").append(stepIcon).append("</span>")
          .append("<span class=\"badge\" style=\"background:#e2e8f0;color:#475569;\">").append(HtmlUtil.esc(role)).append("</span>")
          .append("<span class=\"name\">").append(HtmlUtil.esc(method)).append(" ").append(HtmlUtil.esc(path)).append("</span>")
          .append("<span class=\"meta-info\">").append(HtmlUtil.esc(statusStr)).append("</span>");
        if (skipped) {
            sb.append("<span class=\"badge\" style=\"background:#d1d5db;color:#6b7280;\">SKIPPED</span>");
        }
        sb.append("</summary>\n<div class=\"details-body\">\n");

        if (skipped) {
            sb.append("<div class=\"ai-field\"><strong>Skip reason:</strong> ")
              .append(HtmlUtil.esc(sr.getSkipReason())).append("</div>\n");
        }

        // Response
        HttpResponse resp = sr.getResponse();
        if (resp != null) {
            sb.append("<h3>Response (HTTP ").append(resp.getStatusCode()).append(")</h3>\n");
            if (resp.getBody() != null) {
                sb.append("<pre><code>").append(HtmlUtil.esc(HtmlUtil.toJson(resp.getBody()))).append("</code></pre>\n");
            } else if (resp.getRawBody() != null && !resp.getRawBody().isBlank()) {
                sb.append("<pre><code>").append(HtmlUtil.esc(resp.getRawBody())).append("</code></pre>\n");
            }
        }

        // Assertions
        List<AssertionResult> assertions = sr.getAssertionResults();
        if (assertions != null && !assertions.isEmpty()) {
            sb.append("<h3>Assertions</h3>\n")
              .append("<table class=\"assertions\"><thead><tr>")
              .append("<th>Path</th><th>Type</th><th>Expected</th><th>Actual</th><th>Status</th>")
              .append("</tr></thead><tbody>\n");
            for (AssertionResult ar : assertions) {
                String rowClass = ar.isPassed() ? "" : " class=\"assert-failed\"";
                String mark = ar.isPassed() ? "✅" : "❌";
                String path2 = ar.getAssertion() != null ? ar.getAssertion().getPath() : "";
                String type = ar.getAssertion() != null ? ar.getAssertion().getType() : "";
                String expected = ar.getAssertion() != null ? String.valueOf(ar.getAssertion().getExpected()) : "";
                sb.append("<tr").append(rowClass).append(">")
                  .append("<td><code>").append(HtmlUtil.esc(path2)).append("</code></td>")
                  .append("<td><code>").append(HtmlUtil.esc(type)).append("</code></td>")
                  .append("<td><code>").append(HtmlUtil.esc(expected)).append("</code></td>")
                  .append("<td><code>").append(HtmlUtil.esc(ar.getActualValue())).append("</code></td>")
                  .append("<td>").append(mark).append("</td>")
                  .append("</tr>\n");
            }
            sb.append("</tbody></table>\n");
        }

        sb.append("</div>\n</details>\n");
    }
}
