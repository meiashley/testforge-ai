package com.testforge.runner.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class CoverageMetric {
    int covered;
    int missed;
    int total;
    double percentage;

    @JsonCreator
    public CoverageMetric(@JsonProperty("covered") int covered,
                          @JsonProperty("missed") int missed,
                          @JsonProperty("total") int total,
                          @JsonProperty("percentage") double percentage) {
        this.covered = covered;
        this.missed = missed;
        this.total = total;
        this.percentage = percentage;
    }

    public static CoverageMetric of(int covered, int missed) {
        int total = covered + missed;
        if (total <= 0) {
            throw new IllegalArgumentException("Coverage metric total must be greater than zero");
        }
        return new CoverageMetric(covered, missed, total, covered * 100.0 / total);
    }
}
