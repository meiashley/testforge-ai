package com.testforge.gateway.controller;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GenerateTestsResponse {

    private String jobId;
    private String status;
    private String statusUrl;
}
