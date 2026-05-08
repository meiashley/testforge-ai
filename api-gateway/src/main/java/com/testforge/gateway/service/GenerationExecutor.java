package com.testforge.gateway.service;

import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.client.RealClaudeClient;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.parser.ResponseParser;
import com.testforge.ai.pipeline.SwaggerOpenApiLoader;
import com.testforge.ai.pipeline.TestGenerationPipeline;
import com.testforge.ai.prompt.EndpointPromptBuilder;
import com.testforge.ai.prompt.PromptBuilder;
import com.testforge.ai.prompt.PromptBuilderV2;
import com.testforge.ai.prompt.PromptBuilderV3;
import com.testforge.runner.assertion.AssertionEvaluator;
import com.testforge.runner.http.HttpExecutor;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.pipeline.ExecutionPipeline;
import com.testforge.runner.report.ReportBuilder;
import com.testforge.runner.report.ReportWriter;
import com.testforge.runner.setup.SetupRunner;
import com.testforge.gateway.job.Job;
import com.testforge.gateway.job.JobStatus;
import com.testforge.gateway.job.JobStore;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class GenerationExecutor {

    static final String BASE_URL = "http://localhost:8080";

    private final JobStore jobStore;
    private final OkHttpClient httpClient;

    public GenerationExecutor(JobStore jobStore, OkHttpClient httpClient) {
        this.jobStore = jobStore;
        this.httpClient = httpClient;
    }

    @Async
    public void executeJobAsync(String jobId, String openApiUrl, String promptVersion) {
        Job job = jobStore.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.RUNNING);
        jobStore.update(job);

        try {
            String yaml = fetchYaml(openApiUrl);

            EndpointPromptBuilder promptBuilder = resolvePromptBuilder(promptVersion);
            ClaudeClient claudeClient = new RealClaudeClient(System.getenv("ANTHROPIC_API_KEY"));
            TestGenerationPipeline pipeline = new TestGenerationPipeline(
                    new SwaggerOpenApiLoader(), promptBuilder, claudeClient, new ResponseParser());

            List<GenerationResult> results = pipeline.run(yaml);

            ExecutionReport report;
            if ("V3".equals(promptVersion) || "V3.1".equals(promptVersion)) {
                report = new ExecutionPipeline(
                        new HttpExecutor(), new AssertionEvaluator(),
                        new ReportBuilder(), new ReportWriter("job-" + jobId))
                        .run(results, BASE_URL, new SetupRunner());
            } else {
                report = new ExecutionPipeline(
                        new HttpExecutor(), new AssertionEvaluator(),
                        new ReportBuilder(), new ReportWriter("job-" + jobId))
                        .run(results, BASE_URL);
            }

            job.setReport(report);
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            jobStore.update(job);

        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.toString());
            job.setCompletedAt(Instant.now());
            jobStore.update(job);
        }
    }

    EndpointPromptBuilder resolvePromptBuilder(String promptVersion) {
        return switch (promptVersion) {
            case "V1" -> new PromptBuilder();
            case "V2" -> new PromptBuilderV2();
            case "V3", "V3.1" -> new PromptBuilderV3();
            default -> throw new IllegalArgumentException("Unknown promptVersion: " + promptVersion);
        };
    }

    private String fetchYaml(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("Failed to fetch OpenAPI spec from: " + url);
            }
            return response.body().string();
        }
    }
}
