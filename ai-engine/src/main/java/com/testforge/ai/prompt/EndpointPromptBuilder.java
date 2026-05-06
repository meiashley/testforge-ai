package com.testforge.ai.prompt;

import com.testforge.ai.model.EndpointSpec;

public interface EndpointPromptBuilder {
    String build(EndpointSpec spec);
}
