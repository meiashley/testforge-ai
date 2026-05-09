package com.testforge.runner.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.testforge.ai.model.TestCaseRequest;
import com.testforge.runner.model.AssertionResult;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.HttpResponse;
import com.testforge.runner.model.TestCaseResult;
import com.testforge.runner.model.TestResultStatus;

public class HtmlReportGenerator {

    private static final ObjectMapper PRETTY_JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public String generate(ExecutionReport report) {
        var s = report.getSummary();
        double rate = s.getPassRate();
        String passRateColor = rate >= 0.9 ? "#16a34a" : rate >= 0.7 ? "#f59e0b" : "#dc2626";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
          .append("<title>TestForge AI - Execution Report</title>\n")
          .append("<style>\n")
          .append("*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }\n")
          .append("body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif;")
          .append(" background: #f1f5f9; color: #1e293b; padding: 24px; max-width: 1100px; margin: 0 auto; }\n")
          .append("h1 { font-size: 1.75rem; color: #1e40af; margin-bottom: 4px; }\n")
          .append("h2 { font-size: 1.1rem; color: #1e40af; margin: 24px 0 12px; }\n")
          .append("h3 { font-size: 0.95rem; color: #1e40af; margin: 12px 0 6px; }\n")
          .append(".meta { color: #6b7280; font-size: 0.875rem; margin-bottom: 24px; }\n")
          .append(".cards { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 24px; }\n")
          .append(".card { background: #fff; border-radius: 8px; padding: 20px 24px;")
          .append(" box-shadow: 0 1px 3px rgba(0,0,0,.1); min-width: 130px; flex: 1; }\n")
          .append(".card .label { font-size: 0.75rem; color: #6b7280; text-transform: uppercase;")
          .append(" letter-spacing: .05em; margin-bottom: 6px; }\n")
          .append(".card .value { font-size: 2rem; font-weight: 700; }\n")
          .append(".card.rate .value { color: ").append(passRateColor).append("; }\n")
          .append("details { background: #fff; border-radius: 8px; margin-bottom: 8px;")
          .append(" box-shadow: 0 1px 3px rgba(0,0,0,.1); overflow: hidden; }\n")
          .append("details[open] { padding-bottom: 16px; }\n")
          .append("summary { padding: 12px 16px; cursor: pointer; font-size: 0.9rem;")
          .append(" display: flex; align-items: center; gap: 12px; user-select: none; }\n")
          .append("summary:hover { background: #f8fafc; }\n")
          .append("summary.failed { background: #fee2e2; }\n")
          .append("summary.failed:hover { background: #fecaca; }\n")
          .append(".badge { display: inline-block; padding: 2px 8px; border-radius: 9999px;")
          .append(" font-size: 0.7rem; font-weight: 600; text-transform: uppercase; }\n")
          .append(".badge-HAPPY_PATH { background: #dbeafe; color: #1e40af; }\n")
          .append(".badge-BOUNDARY { background: #ede9fe; color: #6d28d9; }\n")
          .append(".badge-NEGATIVE { background: #fed7aa; color: #c2410c; }\n")
          .append(".badge-SECURITY { background: #fecaca; color: #991b1b; }\n")
          .append(".details-body { padding: 0 16px; }\n")
          .append("pre { background: #f8fafc; padding: 12px; border-radius: 6px;")
          .append(" overflow-x: auto; font-size: 0.8rem; line-height: 1.4;")
          .append(" border: 1px solid #e2e8f0; }\n")
          .append("table.assertions { width: 100%; border-collapse: collapse; margin-top: 4px; }\n")
          .append("table.assertions th { background: #1e40af; color: #fff; text-align: left;")
          .append(" padding: 6px 10px; font-size: 0.75rem; text-transform: uppercase; }\n")
          .append("table.assertions td { padding: 6px 10px; font-size: 0.825rem;")
          .append(" border-bottom: 1px solid #f1f5f9; vertical-align: top; }\n")
          .append("table.assertions tr.assert-failed { background: #fef2f2; }\n")
          .append("code { font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;")
          .append(" font-size: 0.85em; }\n")
          .append(".name { flex: 1; }\n")
          .append(".meta-info { color: #6b7280; font-size: 0.75rem; }\n")
          .append("footer { margin-top: 32px; text-align: center; color: #6b7280; font-size: 0.8rem; }\n")
          .append("@media (max-width: 640px) { .cards { flex-direction: column; }")
          .append(" summary { flex-wrap: wrap; } body { padding: 12px; } }\n")
          .append("</style>\n</head>\n<body>\n");

        // Hero
        sb.append("<h1>TestForge AI Execution Report</h1>\n");
        sb.append("<p class=\"meta\">Generated ").append(esc(s.getExecutedAt()))
          .append(" &nbsp;|&nbsp; Duration ").append(s.getTotalDurationMs()).append("ms</p >\n");

        // Summary cards
        sb.append("<div class=\"cards\">\n");
        sb.append(card("Total",     String.valueOf(s.getTotal()),  ""));
        sb.append(card("Passed",    String.valueOf(s.getPassed()), ""));
        sb.append(card("Failed",    String.valueOf(s.getFailed()), ""));
        sb.append(card("Pass Rate", String.format("%.1f%%", s.getPassRate() * 100), " rate"));
        sb.append("</div>\n");

        // Test Cases as collapsible details
        sb.append("<h2>Test Cases</h2>\n");
        for (TestCaseResult r : report.getResults()) {
            renderTestCase(sb, r);
        }

        sb.append("<footer>Generated by TestForge AI &middot; github.com/meiashley/testforge-ai</footer>\n");
        sb.append("</body>\n</html>");

        return sb.toString();
    }

    private void renderTestCase(StringBuilder sb, TestCaseResult r) {
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
          .append("<span class=\"name\">").append(esc(r.getName())).append("</span>")
          .append("<span class=\"badge badge-").append(type).append("\">").append(type).append("</span>")
          .append("<span class=\"meta-info\">").append(r.getDurationMs()).append("ms</span>")
          .append("<span class=\"meta-info\">").append(assertPassed).append("/").append(assertTotal).append("</span>")
          .append("</summary>\n");

        sb.append("<div class=\"details-body\">\n");

        // Request
        if (r.getRequest() != null) {
            TestCaseRequest req = r.getRequest();
            sb.append("<h3>Request</h3>\n");
            sb.append("<pre><code>").append(esc(req.getMethod())).append(" ").append(esc(req.getPath()));
            if (req.getBody() != null) {
                sb.append("\n\n").append(esc(toJson(req.getBody())));
            }
            sb.append("</code></pre>\n");
        }

        // Response
        if (r.getHttpResponse() != null) {
            HttpResponse resp = r.getHttpResponse();
            sb.append("<h3>Response (HTTP ").append(resp.getStatusCode())
              .append(", ").append(resp.getDurationMs()).append("ms)</h3>\n");
            if (resp.getBody() != null) {
                sb.append("<pre><code>").append(esc(toJson(resp.getBody()))).append("</code></pre>\n");
            } else if (resp.getRawBody() != null) {
                sb.append("<pre><code>").append(esc(resp.getRawBody())).append("</code></pre>\n");
            }
        }

        // Assertions
        if (r.getAssertionResults() != null && !r.getAssertionResults().isEmpty()) {
            sb.append("<h3>Assertions (").append(assertPassed).append("/").append(assertTotal).append(" passed)</h3>\n");
            sb.append("<table class=\"assertions\">\n")
              .append("<thead><tr><th>Field</th><th>Expected</th><th>Actual</th><th>Status</th></tr></thead>\n")
              .append("<tbody>\n");
            for (AssertionResult ar : r.getAssertionResults()) {
                String rowClass = ar.isPassed() ? "" : " class=\"assert-failed\"";
                String mark = ar.isPassed() ? "&#x2705;" : "&#x274C;";
                sb.append("<tr").append(rowClass).append(">")
                  .append("<td><code>").append(esc(ar.getField())).append("</code></td>")
                  .append("<td><code>").append(esc(String.valueOf(ar.getExpected()))).append("</code></td>")
                  .append("<td><code>").append(esc(String.valueOf(ar.getActual()))).append("</code></td>")
                  .append("<td>").append(mark).append("</td>")
                  .append("</tr>\n");
            }
            sb.append("</tbody></table>\n");
        }

        sb.append("</div>\n</details>\n");
    }

    private static String card(String label, String value, String extraClass) {
        return "<div class=\"card" + extraClass + "\"><div class=\"label\">" + label
             + "</div><div class=\"value\">" + value + "</div></div>\n";
    }

    private static String esc(Object o) {
        if (o == null) return "";
        String s = o.toString();
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String toJson(Object obj) {
        try {
            return PRETTY_JSON.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return String.valueOf(obj);
        }
    }
}
