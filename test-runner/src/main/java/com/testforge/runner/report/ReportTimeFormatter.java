package com.testforge.runner.report;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class ReportTimeFormatter {

    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter
            .ofPattern("d MMM uuuu, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private ReportTimeFormatter() {
    }

    public static String formatUtc(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return "-";
        }
        try {
            return UTC_FORMATTER.format(Instant.parse(timestamp));
        } catch (DateTimeParseException e) {
            return timestamp;
        }
    }
}
