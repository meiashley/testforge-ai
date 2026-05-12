package com.testforge.ai.validation;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContractViolation {
    private String testCaseId;
    private String testCaseName;
    private String violationType;
    private String details;
}
