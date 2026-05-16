package com.testforge.runner.report;

import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.report.sections.ReportSection;

import java.util.List;

public class NavigationRenderer {

    public String render(List<ReportSection> sections, ExecutionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<nav class=\"report-nav\">\n");
        for (ReportSection section : sections) {
            if (section.hasContent(report)) {
                sb.append("<a href=\"#").append(section.getSectionId()).append("\">")
                  .append(section.getIcon()).append(" ").append(section.getNavLabel())
                  .append("</a>\n");
            }
        }
        sb.append("</nav>\n");
        return sb.toString();
    }
}
