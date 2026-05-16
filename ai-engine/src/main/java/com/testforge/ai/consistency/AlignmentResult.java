package com.testforge.ai.consistency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlignmentResult {
    private List<ConsistencyMismatch> mismatches;
    private int totalConstraints;
    private int alignedCount;
    private int constraintsWithMismatchCount;
    private int mismatchCount;
    private Map<String, Integer> severityBreakdown;
    private Map<String, Integer> categoryBreakdown;
}
