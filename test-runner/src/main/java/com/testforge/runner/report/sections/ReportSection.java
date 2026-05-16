package com.testforge.runner.report.sections;

import com.testforge.runner.model.ExecutionReport;

public interface ReportSection {
    String render(ExecutionReport report);
    String getSectionId();
    String getNavLabel();
    String getIcon();

    default boolean hasContent(ExecutionReport report) {
        return true;
    }
}
