package com.testforge.runner.report;

import com.testforge.runner.model.AssertionResult;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.TestCaseResult;
import com.testforge.runner.model.TestResultStatus;

import java.util.LinkedHashMap;
import java.util.Map;

public class HtmlReportGenerator {

    public String generate(ExecutionReport report) {
        var s = report.getSummary();
        String passRateColor = s.getPassRate() >= 0.9 ? "#16a34a" : s.getPassRate() >= 0.7 ? "#f59e0b" : "#dc2626";

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
          .append("<title>TestForge AI - Execution Report</title>\n")
          .append("<style>\n")
          .append("*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }\n")
          .append("body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif;")
          .append(" background: #f1f5f9; color: #1e293b; padding: 24px; }\n")
          .append("h1 { font-size: 1.75rem; color: #1e40af; margin-bottom: 4px; }\n")
          .append("h2 { font-size: 1.1rem; color: #1e40af; margin: 24px 0 12px; }\n")
          .append(".meta { color: #6b7280; font-size: 0.875rem; margin-bottom: 24px; }\n")
          .append(".cards { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 24px; }\n")
          .append(".card { background: #fff; border-radius: 8px; padding: 20px 24px;")
          .append(" box-shadow: 0 1px 3px rgba(0,0,0,.1); min-width: 130px; flex: 1; }\n")
          .append(".card .label { font-size: 0.75rem; color: #6b7280; text-transform: uppercase;")
          .append(" letter-spacing: .05em; margin-bottom: 6px; }\n")
          .append(".card .value { font-size: 2rem; font-weight: 700; }\n")
          .append(".card.rate .value { color: ").append(passRateColor).append("; }\n")
          .append("table { width: 100%; border-collapse: collapse; background: #fff;")
          .append(" border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.1); }\n")
          .append("th { background: #1e40af; color: #fff; text-align: left;")
          .append(" padding: 10px 14px; font-size: 0.8rem; text-transform: uppercase; }\n")
          .append("td { padding: 10px 14px; font-size: 0.875rem; border-bottom: 1px solid #f1f5f9; }\n")
          .append("tr.failed { background: #fee2e2; }\n")
          .append(".PASSED { color: #16a34a; font-weight: 600; }\n")
          .append(".FAILED { color: #dc2626; font-weight: 600; }\n")
          .append(".ERROR  { color: #f59e0b; font-weight: 600; }\n")
          .append("footer { margin-top: 32px; text-align: center; color: #6b7280; font-size: 0.8rem; }\n")
          .append("@media (max-width: 640px) { .cards { flex-direction: column; } }\n")
          .append("</style>\n</head>\n<body>\n");

        // Hero
        sb.append("<h1>TestForge AI Execution Report</h1>\n");
        sb.append("<p class=\"meta\">Generated ").append(esc(s.getExecutedAt()))
          .append(" &nbsp;|&nbsp; Duration ").append(s.getTotalDurationMs()).append("ms</p>\n");

        // Summary cards
        sb.append("<div class=\"cards\">\n");
        sb.append(card("Total",     String.valueOf(s.getTotal()),  ""));
        sb.append(card("Passed",    String.valueOf(s.getPassed()), ""));
        sb.append(card("Failed",    String.valueOf(s.getFailed()), ""));
        sb.append(card("Pass Rate", String.format("%.1f%%", s.getPassRate() * 100), " rate"));
        sb.append("</div>\n");

        // Test cases table
        sb.append("<h2>Test Cases</h2>\n")
          .append("<table>\n<thead><tr>")
          .append("<th>Name</th><th>Type</th><th>Status</th><th>Duration</th><th>Assertions</th>")
          .append("</tr></thead>\n<tbody>\n");

        for (TestCaseResult r : report.getResults()) {
            String rowClass = r.getStatus() == TestResultStatus.PASSED ? "" : " class=\"failed\"";
            String icon = r.getStatus() == TestResultStatus.PASSED ? "&#x2705;" : "&#x274C;";
            int assertPassed = 0, assertTotal = 0;
            if (r.getAssertionResults() != null) {
                assertTotal = r.getAssertionResults().size();
                assertPassed = (int) r.getAssertionResults().stream()
                        .filter(AssertionResult::isPassed).count();
            }
            sb.append("<tr").append(rowClass).append(">")
              .append("<td>").append(icon).append(" ").append(esc(r.getName())).append("</td>")
              .append("<td>").append(r.getType() != null ? esc(r.getType().name()) : "").append("</td>")
              .append("<td class=\"").append(r.getStatus().name()).append("\">")
              .append(r.getStatus().name()).append("</td>")
              .append("<td>").append(r.getDurationMs()).append("ms</td>")
              .append("<td>").append(assertPassed).append("/").append(assertTotal).append("</td>")
              .append("</tr>\n");
        }

        sb.append("</tbody></table>\n");
        sb.append("<footer>Generated by TestForge AI &middot; github.com/meiashley/testforge-ai</footer>\n");
        sb.append("</body>\n</html>");

        return sb.toString();
    }

    private static String card(String label, String value, String extraClass) {
        return "<div class=\"card" + extraClass + "\"><div class=\"label\">" + label
             + "</div><div class=\"value\">" + value + "</div></div>\n";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

}
