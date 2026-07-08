package com.testforge.runner.report;

import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportTimeFormatterTest {

    @Test
    void formatsIsoTimestampInUtcIndependentOfDefaultTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Australia/Sydney"));

            String formatted = ReportTimeFormatter.formatUtc("2026-07-08T02:12:41.841590Z");

            assertEquals("8 Jul 2026, 02:12 UTC", formatted);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void nullTimestampFormatsAsDash() {
        assertEquals("-", ReportTimeFormatter.formatUtc(null));
    }
}
