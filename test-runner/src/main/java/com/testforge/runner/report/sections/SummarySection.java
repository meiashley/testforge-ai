package com.testforge.runner.report.sections;

import com.testforge.runner.execution.PlanExecutionResult;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;

import java.util.List;

public class SummarySection implements ReportSection {

    @Override
    public String getSectionId() { return "summary"; }

    @Override
    public String getNavLabel() { return "Summary"; }

    @Override
    public String getIcon() { return "📊"; }

    @Override
    public String render(ExecutionReport report) {
        ExecutionSummary s = report.getSummary();
        double rate = s.getPassRate();
        String passRateColor = rate >= 0.9 ? "#16a34a" : rate >= 0.7 ? "#f59e0b" : "#dc2626";

        // Scenario pass stats
        List<PlanExecutionResult> scenarios = report.getScenarioResults();
        String scenarioCell;
        if (scenarios == null || scenarios.isEmpty()) {
            scenarioCell = HtmlUtil.card("Scenarios", "—", "");
        } else {
            long scenarioPassed = scenarios.stream().filter(PlanExecutionResult::isPassed).count();
            String scenarioColor = scenarioPassed == scenarios.size() ? "#16a34a" : "#dc2626";
            scenarioCell = "<div class=\"card\"><div class=\"label\">Scenarios</div>"
                    + "<div class=\"value\" style=\"color:" + scenarioColor + "\">"
                    + scenarioPassed + "/" + scenarios.size() + "</div></div>\n";
        }

        // Consistency mismatch count
        int mismatchCount = (report.getConsistencyResult() != null)
                ? report.getConsistencyResult().getMismatchCount() : 0;
        String mismatchColor = mismatchCount == 0 ? "#16a34a" : "#dc2626";

        // AI diagnosis count
        int diagnosisCount = (report.getFailureAnalysis() != null)
                ? report.getFailureAnalysis().size() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("<p class=\"meta\">Generated ").append(HtmlUtil.esc(s.getExecutedAt()))
          .append(" &nbsp;|&nbsp; Duration ").append(s.getTotalDurationMs()).append("ms</p>\n");
        sb.append("<div class=\"cards\">\n");
        sb.append(HtmlUtil.card("API Tests Total",  String.valueOf(s.getTotal()),  ""));
        sb.append(HtmlUtil.card("API Tests Passed", String.valueOf(s.getPassed()), ""));
        sb.append(HtmlUtil.card("API Tests Failed", String.valueOf(s.getFailed()), ""));
        sb.append("<div class=\"card rate\"><div class=\"label\">Pass Rate</div>"
                + "<div class=\"value\" style=\"color:" + passRateColor + "\">"
                + String.format("%.1f%%", rate * 100) + "</div></div>\n");
        sb.append(scenarioCell);
        sb.append("<div class=\"card\"><div class=\"label\">Mismatches</div>"
                + "<div class=\"value\" style=\"color:" + mismatchColor + "\">"
                + mismatchCount + "</div></div>\n");
        sb.append(HtmlUtil.card("AI Diagnoses", String.valueOf(diagnosisCount), ""));
        sb.append("</div>\n");
        return sb.toString();
    }
}
