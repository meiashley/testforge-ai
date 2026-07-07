package com.testforge.gateway.service;

import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.pipeline.TestGenerationOutcome;
import com.testforge.ai.validation.GenerationValidationException;
import com.testforge.ai.validation.RejectedTestCase;
import com.testforge.ai.validation.ValidationIssue;
import com.testforge.ai.validation.ValidationSeverity;
import com.testforge.gateway.job.Job;
import com.testforge.gateway.job.JobStatus;
import com.testforge.gateway.job.JobStore;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GenerationExecutorOutcomeDiagnosticsTest {

    private final GenerationExecutor executor = new GenerationExecutor(new NoopJobStore(), new OkHttpClient());

    @Test
    void allAcceptedKeepsAcceptedResultsAndNoDiagnostics() {
        Job job = new Job();
        GenerationResult generation = generation("tc-1");

        executor.applyGenerationOutcome(job, new TestGenerationOutcome(
                List.of(generation), List.of(testCase("tc-1")), List.of(), List.of()));

        assertEquals(1, job.getGenerationResults().size());
        assertEquals("tc-1", job.getGenerationResults().get(0).getTestCases().get(0).getId());
        assertTrue(job.getRejectedTestCases().isEmpty());
        assertTrue(job.getValidationWarnings().isEmpty());
        assertFalse(job.isPartialAcceptance());
    }

    @Test
    void acceptedPlusRejectedPropagatesStructuredDiagnostics() {
        Job job = new Job();
        ValidationIssue issue = ValidationIssue.error("REQUEST_REQUIRED",
                "testCases[1].request", "Request object must be present");
        RejectedTestCase rejected = new RejectedTestCase(1, "tc-2", List.of(issue));

        executor.applyGenerationOutcome(job, new TestGenerationOutcome(
                List.of(generation("tc-1")), List.of(testCase("tc-1")), List.of(rejected), List.of()));

        assertEquals(1, job.getGenerationResults().size());
        assertEquals(1, job.getRejectedTestCases().size());
        RejectedTestCase diagnostic = job.getRejectedTestCases().get(0);
        assertEquals(1, diagnostic.getBatchIndex());
        assertEquals("tc-2", diagnostic.getTestCaseId());
        assertEquals("REQUEST_REQUIRED", diagnostic.getValidationIssues().get(0).getCode());
        assertEquals("testCases[1].request", diagnostic.getValidationIssues().get(0).getFieldPath());
        assertEquals("Request object must be present", diagnostic.getValidationIssues().get(0).getMessage());
        assertEquals(ValidationSeverity.ERROR, diagnostic.getValidationIssues().get(0).getSeverity());
        assertTrue(job.isPartialAcceptance());
    }

    @Test
    void warningsArePropagatedWithoutRejections() {
        Job job = new Job();
        ValidationIssue warning = ValidationIssue.warning("TEST_CASE_SCENARIO_MISSING",
                "testCases[0].scenario", "Test case scenario is recommended for display and diagnostics");

        executor.applyGenerationOutcome(job, new TestGenerationOutcome(
                List.of(generation("tc-1")), List.of(testCase("tc-1")), List.of(), List.of(warning)));

        assertEquals(1, job.getGenerationResults().size());
        assertEquals(1, job.getValidationWarnings().size());
        assertEquals("TEST_CASE_SCENARIO_MISSING", job.getValidationWarnings().get(0).getCode());
        assertFalse(job.isPartialAcceptance());
    }

    @Test
    void duplicateContentDiagnosticsAreAvailableInJobResult() {
        Job job = new Job();
        ValidationIssue duplicate = ValidationIssue.warning("DUPLICATE_EXECUTION_CONTENT",
                "testCases[1]", "Execution content duplicates testCases[0]; later duplicate rejected");
        RejectedTestCase rejected = new RejectedTestCase(1, "tc-2", List.of(duplicate));

        executor.applyGenerationOutcome(job, new TestGenerationOutcome(
                List.of(generation("tc-1")), List.of(testCase("tc-1")),
                List.of(rejected), List.of(duplicate)));

        assertEquals("tc-1", job.getGenerationResults().get(0).getTestCases().get(0).getId());
        assertEquals("tc-2", job.getRejectedTestCases().get(0).getTestCaseId());
        assertEquals("DUPLICATE_EXECUTION_CONTENT",
                job.getRejectedTestCases().get(0).getValidationIssues().get(0).getCode());
        assertEquals("DUPLICATE_EXECUTION_CONTENT", job.getValidationWarnings().get(0).getCode());
        assertTrue(job.isPartialAcceptance());
    }

    @Test
    void allContractRejected_marksJobFailedAndPreservesDiagnostics() {
        RecordingJobStore jobStore = new RecordingJobStore();
        GenerationExecutor executor = new GenerationExecutor(jobStore, new OkHttpClient());
        Job job = new Job();
        ValidationIssue issue = ValidationIssue.error("STATUS_NOT_IN_SPEC",
                "testCases[0].expected.status", "Expected status 500 is not in spec response codes [201]");
        RejectedTestCase rejected = new RejectedTestCase(0, "tc-1", List.of(issue));
        TestGenerationOutcome outcome = new TestGenerationOutcome(List.of(), List.of(), List.of(rejected), List.of());

        executor.handleGenerationValidationFailure(job,
                new GenerationValidationException("Generated tests were rejected by contract validation; no accepted test cases remain",
                        outcome));

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("Generated tests were rejected by contract validation; no accepted test cases remain", job.getErrorMessage());
        assertNotNull(job.getCompletedAt());
        assertTrue(job.getGenerationResults().isEmpty());
        assertEquals(1, job.getRejectedTestCases().size());
        assertTrue(job.getValidationWarnings().isEmpty());
        assertFalse(job.isPartialAcceptance());
        assertEquals(1, jobStore.updatedJobs.size());
    }

    @Test
    void contractRejectionDoesNotDisappearFromJobResponse() {
        RecordingJobStore jobStore = new RecordingJobStore();
        GenerationExecutor executor = new GenerationExecutor(jobStore, new OkHttpClient());
        Job job = new Job();
        ValidationIssue warning = ValidationIssue.warning("TEST_CASE_SCENARIO_MISSING",
                "testCases[0].scenario", "Test case scenario is recommended for display and diagnostics");
        ValidationIssue issue = ValidationIssue.error("METHOD_MISMATCH",
                "testCases[1].request.method", "Method 'PATCH' does not match spec method 'POST'");
        RejectedTestCase rejected = new RejectedTestCase(1, "tc-2", List.of(issue));
        TestGenerationOutcome outcome = new TestGenerationOutcome(
                List.of(generation("tc-1")), List.of(testCase("tc-1")), List.of(rejected), List.of(warning));

        executor.handleGenerationValidationFailure(job,
                new GenerationValidationException("Generated test cases include rejected items; use generate() for diagnostics",
                        outcome));

        assertEquals(1, job.getGenerationResults().size());
        assertEquals("tc-1", job.getGenerationResults().get(0).getTestCases().get(0).getId());
        assertEquals(1, job.getRejectedTestCases().size());
        assertEquals(1, job.getRejectedTestCases().get(0).getBatchIndex());
        assertEquals("tc-2", job.getRejectedTestCases().get(0).getTestCaseId());
        assertEquals("METHOD_MISMATCH", job.getRejectedTestCases().get(0).getValidationIssues().get(0).getCode());
        assertEquals("testCases[1].request.method", job.getRejectedTestCases().get(0).getValidationIssues().get(0).getFieldPath());
        assertEquals("Method 'PATCH' does not match spec method 'POST'",
                job.getRejectedTestCases().get(0).getValidationIssues().get(0).getMessage());
        assertEquals(ValidationSeverity.ERROR, job.getRejectedTestCases().get(0).getValidationIssues().get(0).getSeverity());
        assertEquals(1, job.getValidationWarnings().size());
        assertEquals("TEST_CASE_SCENARIO_MISSING", job.getValidationWarnings().get(0).getCode());
        assertTrue(job.isPartialAcceptance());
        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals(1, jobStore.updatedJobs.size());
    }

    @Test
    void structuralFailureWithoutAcceptedMarksJobFailedAndPreservesDiagnostics() {
        RecordingJobStore jobStore = new RecordingJobStore();
        GenerationExecutor executor = new GenerationExecutor(jobStore, new OkHttpClient());
        Job job = new Job();
        ValidationIssue warning = ValidationIssue.warning("TEST_CASE_SCENARIO_MISSING",
                "testCases[0].scenario", "Test case scenario is recommended for display and diagnostics");
        ValidationIssue issue = ValidationIssue.error("UNSUPPORTED_HTTP_METHOD",
                "testCases[0].request.method", "HTTP method 'TRACE' is not supported");
        RejectedTestCase rejected = new RejectedTestCase(0, "tc-1", List.of(warning, issue));
        TestGenerationOutcome outcome = new TestGenerationOutcome(List.of(), List.of(), List.of(rejected), List.of(warning));

        executor.handleGenerationValidationFailure(job,
                new GenerationValidationException("Generated test case batch has no structurally valid test cases",
                        outcome));

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("Generated test case batch has no structurally valid test cases", job.getErrorMessage());
        assertNotNull(job.getCompletedAt());
        assertTrue(job.getGenerationResults().isEmpty());
        assertEquals(1, job.getRejectedTestCases().size());
        assertEquals("tc-1", job.getRejectedTestCases().get(0).getTestCaseId());
        assertEquals("UNSUPPORTED_HTTP_METHOD", job.getRejectedTestCases().get(0).getValidationIssues().get(1).getCode());
        assertEquals(1, job.getValidationWarnings().size());
        assertEquals("TEST_CASE_SCENARIO_MISSING", job.getValidationWarnings().get(0).getCode());
        assertFalse(job.isPartialAcceptance());
        assertEquals(1, jobStore.updatedJobs.size());
    }

    @Test
    void structuralFailureAfterAcceptedEndpointMarksPartialAcceptance() {
        RecordingJobStore jobStore = new RecordingJobStore();
        GenerationExecutor executor = new GenerationExecutor(jobStore, new OkHttpClient());
        Job job = new Job();
        ValidationIssue warning = ValidationIssue.warning("TEST_CASE_SCENARIO_MISSING",
                "testCases[0].scenario", "Test case scenario is recommended for display and diagnostics");
        ValidationIssue issue = ValidationIssue.error("REQUEST_REQUIRED",
                "testCases[0].request", "Request object must be present");
        RejectedTestCase rejected = new RejectedTestCase(0, "tc-2", List.of(warning, issue));
        TestGenerationOutcome outcome = new TestGenerationOutcome(
                List.of(generation("tc-1")), List.of(testCase("tc-1")), List.of(rejected), List.of(warning));

        executor.handleGenerationValidationFailure(job,
                new GenerationValidationException("Generated test case batch failed structural validation",
                        outcome));

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("Generated test case batch failed structural validation", job.getErrorMessage());
        assertEquals(1, job.getGenerationResults().size());
        assertEquals("tc-1", job.getGenerationResults().get(0).getTestCases().get(0).getId());
        assertEquals(1, job.getRejectedTestCases().size());
        assertEquals("tc-2", job.getRejectedTestCases().get(0).getTestCaseId());
        assertEquals(1, job.getValidationWarnings().size());
        assertTrue(job.isPartialAcceptance());
        assertNotNull(job.getCompletedAt());
        assertEquals(1, jobStore.updatedJobs.size());
    }

    private GenerationResult generation(String testCaseId) {
        return new GenerationResult(endpoint(), List.of(testCase(testCaseId)));
    }

    private EndpointSpec endpoint() {
        return EndpointSpec.builder()
                .method("POST")
                .path("/api/payments")
                .operationId("createPayment")
                .summary("Create payment")
                .requestBodySchema("{}")
                .responseSchemas(Map.of("201", "{}"))
                .build();
    }

    private TestCase testCase(String id) {
        TestCase testCase = new TestCase();
        testCase.setId(id);
        return testCase;
    }

    private static class NoopJobStore implements JobStore {
        @Override
        public String save(Job job) {
            return "job";
        }

        @Override
        public Optional<Job> findById(String jobId) {
            return Optional.empty();
        }

        @Override
        public void update(Job job) {
        }
    }

    private static class RecordingJobStore implements JobStore {
        private final List<Job> updatedJobs = new ArrayList<>();

        @Override
        public String save(Job job) {
            return "job";
        }

        @Override
        public Optional<Job> findById(String jobId) {
            return Optional.empty();
        }

        @Override
        public void update(Job job) {
            updatedJobs.add(job);
        }
    }
}
