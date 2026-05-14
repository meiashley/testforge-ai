package com.testforge.ai.consistency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsistencyMismatch {
    private String mismatchId;
    private String category;
    private String severity;
    private String confidence;
    private String summary;
    private String evidence;
    private String location;
    private String requirementReference;
    private String specReference;
}
