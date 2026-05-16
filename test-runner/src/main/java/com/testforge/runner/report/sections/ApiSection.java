package com.testforge.runner.report.sections;

import com.testforge.ai.analysis.FailureAnalysisResult;
import com.testforge.ai.model.TestCaseRequest;
import com.testforge.runner.model.AssertionResult;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.HttpResponse;
import com.testforge.runner.model.TestCaseResult;
import com.testforge.runner.model.TestResultStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ApiSection implements ReportSection {

    @Override
    public String getSectionId() { return "api-tests"; }

    @Override
    public String getNavLabel() { return "API Tests"; }

    @Override
    public String getIcon() { return "⚡"; }

    @Override
    public boolean hasContent(ExecutionReport report) {
        return report.getResults() != null && !report.getResults().isEmpty();
    }

    @Override
    public String render(ExecutionReport report) {
        List<TestCaseResult> results = report.getResults();
        if (results == null || results.isEmpty()) return "";

        // Build lookup map: testCaseId → FailureAnalysisResult
        Map<String, FailureAnalysisResult> analysisMap = Map.of();
        List<FailureAnalysisResult> analyses = report.getFailureAnalysis();
        if (analyses != null && !analyses.isEmpty()) {
            analysisMap = analyses.stream()
                    .collect(Collectors.toMap(
                            FailureAnalysisResult::getTestCaseId,
                            a -> a,
                            (a, b) -> a));
        }

        StringBuilder sb = new StringBuilder();
        for (TestCaseResult r : results) {
            FailureAnalysisResult analysis = analysisMap.get(r.getTestCaseId());
            renderTestCase(sb, r, analysis);
        }
        return sb.toString();
    }

    private void renderTestCase(StringBuilder sb, TestCaseResult r, FailureAnalysisResult analysis) {
        boolean passed = r.getStatus() == TestResultStatus.PASSED;
        String summaryClass = passed ? "" : " class=\"failed\"";
        String icon = passed ? "&#x2705;" : "&#x274C;";
        String type = r.getType() != null ? r.getType().name() : "UNKNOWN";

        int assertPassed = 0, assertTotal = 0;
        if (r.getAssertionResults() != null) {
            assertTotal = r.getAssertionResults().size();
            assertPassed = (int) r.getAssertionResults().stream()
                    .filter(AssertionResult::isPassed).count();
        }

        sb.append("<details>\n")
          .append("<summary").append(summaryClass).append(">")
          .append("<span>").append(icon).append("</span>")
          .append("<span class=\"name\">").append(HtmlUtil.esc(r.getName())).append("</span>")
          .append("<span class=\"badge badge-").append(type).append("\">").append(type).append("</span>")
          .append("<span class=\"meta-info\">").append(r.getDurationMs()).append("ms</span>")
          .append("<span class=\"meta-info\">").append(assertPassed).append("/").append(assertTotal).append("</span>")
          .append("</summary>\n");

        sb.append("<div class=\"details-body\">\n");

        if (r.getRequest() != null) {
            TestCaseRequest req = r.getRequest();
            sb.append("<h3>Request</h3>\n");
            sb.append("<pre><code>").append(HtmlUtil.esc(req.getMethod())).append(" ").append(HtmlUtil.esc(req.getPath()));
            if (req.getBody() != null) {
                sb.append("\n\n").append(HtmlUtil.esc(HtmlUtil.toJson(req.getBody())));
            }
            sb.append("</code></pre>\n");
        }

        if (r.getHttpResponse() != null) {
            HttpResponse resp = r.getHttpResponse();
            sb.append("<h3>Response (HTTP ").append(resp.getStatusCode())
              .append(", ").append(resp.getDurationMs()).append("ms)</h3>\n");
            if (resp.getBody() != null) {
                sb.append("<pre><code>").append(HtmlUtil.esc(HtmlUtil.toJson(resp.getBody()))).append("</code></pre>\n");
            } else if (resp.getRawBody() != null) {
                sb.append("<pre><code>").append(HtmlUtil.esc(resp.getRawBody())).append("</code></pre>\n");
            }
        }

        if (r.getAssertionResults() != null && !r.getAssertionResults().isEmpty()) {
            sb.append("<h3>Assertions (").append(assertPassed).append("/").append(assertTotal).append(" passed)</h3>\n");
            sb.append("<table class=\"assertions\">\n")
              .append("<thead><tr><th>Field</th><th>Expected</th><th>Actual</th><th>Status</th></tr></thead>\n")
              .append("<tbody>\n");
            for (AssertionResult ar : r.getAssertionResults()) {
                String rowClass = ar.isPassed() ? "" : " class=\"assert-failed\"";
                String mark = ar.isPassed() ? "&#x2705;" : "&#x274C;";
                sb.append("<tr").append(rowClass).append(">")
                  .append("<td><code>").append(HtmlUtil.esc(ar.getField())).append("</code></td>")
                  .append("<td><code>").append(HtmlUtil.esc(String.valueOf(ar.getExpected()))).append("</code></td>")
                  .append("<td><code>").append(HtmlUtil.esc(String.valueOf(ar.getActual()))).append("</code></td>")
                  .append("<td>").append(mark).append("</td>")
                  .append("</tr>\n");
            }
            sb.append("</tbody></table>\n");
        }

        if (!passed && analysis != null) {
            renderAiAnalysis(sb, analysis);
        }

        sb.append("</div>\n</details>\n");
    }

    private void renderAiAnalysis(StringBuilder sb, FailureAnalysisResult a) {
        String category = a.getRootCauseCategory() != null ? a.getRootCauseCategory() : "UNCERTAIN";
        String confidence = a.getConfidence() != null ? a.getConfidence() : "LOW";
        String categoryLabel = category.replace("_", " ");

        sb.append("<details class=\"ai-analysis-details\">\n")
          .append("<summary class=\"ai-analysis-summary\">")
          .append("&#x1F916; AI Analysis &mdash; ")
          .append("<span class=\"category-badge category-").append(HtmlUtil.esc(category)).append("\">")
          .append(HtmlUtil.esc(categoryLabel)).append("</span>")
          .append("&nbsp;<span class=\"confidence-badge confidence-").append(HtmlUtil.esc(confidence)).append("\">")
          .append(HtmlUtil.esc(confidence)).append(" confidence</span>")
          .append("</summary>\n")
          .append("<div class=\"ai-analysis-body\">\n");

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
