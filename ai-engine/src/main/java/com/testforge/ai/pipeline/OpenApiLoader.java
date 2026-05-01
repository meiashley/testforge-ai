package com.testforge.ai.pipeline;

import com.testforge.ai.model.EndpointSpec;
import java.util.List;

public interface OpenApiLoader {
    List<EndpointSpec> parse(String yamlContent);
}
