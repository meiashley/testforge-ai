package com.testforge.gateway.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GenerateTestsRequest {

    @NotBlank(message = "openApiUrl must not be blank")
    private String openApiUrl;

    private String promptVersion = "V3.1";
}
