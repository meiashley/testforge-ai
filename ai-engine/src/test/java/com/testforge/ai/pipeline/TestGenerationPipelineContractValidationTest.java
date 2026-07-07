package com.testforge.ai.pipeline;

import com.testforge.ai.cache.EndpointCache;
import com.testforge.ai.cache.SpecFingerprint;
import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.parser.ResponseParser;
import com.testforge.ai.prompt.EndpointPromptBuilder;
import com.testforge.ai.validation.ContractViolation;
import com.testforge.ai.validation.GenerationValidationException;
import com.testforge.ai.validation.RejectedTestCase;
import com.testforge.ai.validation.TestCaseContractValidator;
import com.testforge.ai.validation.ValidationSeverity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestGenerationPipelineContractValidationTest {

    @Test
    void partialContractRejection_isIncludedInOutcome() {
        RecordingCache cache = new RecordingCache();
        RecordingContractValidator contractValidator = new RecordingContractValidator(Map.of(
                "tc-2", List.of(contractViolation("tc-2", "PATH_NOT_FOUND",
                        "Path '/api/other' does not match spec path '/api/payments'"))
        ));
        TestGenerationPipeline pipeline = pipeline(List.of(endpoint("/api/payments")), cache, contractValidator, """
                [
                  {"id":"tc-1","name":"Create payment","type":"HAPPY_PATH","priority":"P0","scenario":"s1",
                   "request":{"method":"POST","path":"/api/payments","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}},
                  {"id":"tc-2","name":"Create payment variant","type":"HAPPY_PATH","priority":"P0","scenario":"s2",
                   "request":{"method":"POST","path":"/api/other","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """);

        TestGenerationOutcome outcome = pipeline.generate("openapi: 3.0.0");

        assertEquals(1, outcome.getAcceptedTestCases().size());
        assertEquals("tc-1", outcome.getAcceptedTestCases().get(0).getId());
        assertEquals(1, outcome.getRejectedTestCases().size());
        assertEquals(1, outcome.getRejectedTestCases().get(0).getBatchIndex());
        assertEquals("tc-2", outcome.getRejectedTestCases().get(0).getTestCaseId());
        assertEquals(ValidationSeverity.ERROR, outcome.getRejectedTestCases().get(0).getValidationIssues().get(0).getSeverity());
        assertEquals("PATH_NOT_FOUND", outcome.getRejectedTestCases().get(0).getValidationIssues().get(0).getCode());
        assertEquals("testCases[1].request.path", outcome.getRejectedTestCases().get(0).getValidationIssues().get(0).getFieldPath());
        assertEquals("Path '/api/other' does not match spec path '/api/payments'",
                outcome.getRejectedTestCases().get(0).getValidationIssues().get(0).getMessage());
        assertEquals(1, outcome.getGenerationResults().size());
        assertEquals(1, outcome.getGenerationResults().get(0).getTestCases().size());
        assertEquals("tc-1", outcome.getGenerationResults().get(0).getTestCases().get(0).getId());
        assertEquals(1, cache.saved.size());
        assertEquals(1, cache.saved.get(0).size());
        assertEquals("tc-1", cache.saved.get(0).get(0).getId());
    }

    @Test
    void multipleContractViolations_areGroupedPerTestCase() {
        RecordingCache cache = new RecordingCache();
        RecordingContractValidator contractValidator = new RecordingContractValidator(Map.of(
                "tc-2", List.of(
                        contractViolation("tc-2", "METHOD_MISMATCH", "Method 'PATCH' does not match spec method 'POST'"),
                        contractViolation("tc-2", "STATUS_NOT_IN_SPEC", "Expected status 400 is not in spec response codes [201]")
                )
        ));
        TestGenerationPipeline pipeline = pipeline(List.of(endpoint("/api/payments")), cache, contractValidator, """
                [
                  {"id":"tc-1","name":"Create payment","type":"HAPPY_PATH","priority":"P0","scenario":"s1",
                   "request":{"method":"POST","path":"/api/payments","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}},
                  {"id":"tc-2","name":"Create payment variant","type":"HAPPY_PATH","priority":"P0","scenario":"s2",
                   "request":{"method":"PATCH","path":"/api/payments","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":400,"bodyAssertions":{"id":"non-null"}}}
                ]
                """);

        TestGenerationOutcome outcome = pipeline.generate("openapi: 3.0.0");

        assertEquals(1, outcome.getRejectedTestCases().size());
        RejectedTestCase rejected = outcome.getRejectedTestCases().get(0);
        assertEquals(1, rejected.getBatchIndex());
        assertEquals("tc-2", rejected.getTestCaseId());
        assertEquals(2, rejected.getValidationIssues().size());
        assertEquals("METHOD_MISMATCH", rejected.getValidationIssues().get(0).getCode());
        assertEquals("STATUS_NOT_IN_SPEC", rejected.getValidationIssues().get(1).getCode());
        assertEquals(1, outcome.getAcceptedTestCases().size());
        assertEquals(1, outcome.getGenerationResults().size());
    }

    @Test
    void allTestsForOneEndpointRejected_doesNotCacheOrCreateEmptyResult() {
        RecordingCache cache = new RecordingCache();
        TestCaseContractValidator contractValidator = new TestCaseContractValidator() {
            @Override
            public List<ContractViolation> validate(List<TestCase> tests, EndpointSpec spec) {
                if ("/api/charges".equals(spec.getPath())) {
                    return tests.stream()
                            .map(test -> contractViolation(test.getId(), "PATH_NOT_FOUND",
                                    "Path '/api/payments' does not match spec path '/api/charges'"))
                            .toList();
                }
                return List.of();
            }
        };
        cache.put("/api/charges", """
                [
                  {"id":"pay-1","name":"Charge payment","type":"HAPPY_PATH","priority":"P0","scenario":"s1",
                   "request":{"method":"POST","path":"/api/payments","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """);
        cache.put("/api/refunds", """
                [
                  {"id":"refund-1","name":"Refund payment","type":"HAPPY_PATH","priority":"P0","scenario":"s2",
                   "request":{"method":"POST","path":"/api/refunds","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """);
        TestGenerationPipeline pipeline = pipeline(List.of(
                endpoint("/api/charges"),
                endpoint("/api/refunds")
        ), cache, contractValidator, "[]");

        TestGenerationOutcome outcome = pipeline.generate("openapi: 3.0.0");

        assertEquals(1, outcome.getGenerationResults().size());
        assertEquals("/api/refunds", outcome.getGenerationResults().get(0).getEndpoint().getPath());
        assertEquals(1, outcome.getAcceptedTestCases().size());
        assertEquals("refund-1", outcome.getAcceptedTestCases().get(0).getId());
        assertEquals(1, outcome.getRejectedTestCases().size());
        assertEquals("pay-1", outcome.getRejectedTestCases().get(0).getTestCaseId());
        assertTrue(cache.saved.isEmpty());
        assertTrue(outcome.getGenerationResults().stream().allMatch(result -> !result.getTestCases().isEmpty()));
    }

    @Test
    void allGeneratedTestsContractInvalid_failsClosedWithOutcome() {
        RecordingCache cache = new RecordingCache();
        TestCaseContractValidator contractValidator = new TestCaseContractValidator() {
            @Override
            public List<ContractViolation> validate(List<TestCase> tests, EndpointSpec spec) {
                if ("/api/charges".equals(spec.getPath())) {
                    return tests.stream()
                            .map(test -> contractViolation(test.getId(), "PATH_NOT_FOUND",
                                    "Path '/api/payments' does not match spec path '/api/charges'"))
                            .toList();
                }
                return tests.stream()
                        .map(test -> contractViolation(test.getId(), "STATUS_NOT_IN_SPEC",
                                "Expected status 500 is not in spec response codes [201]"))
                        .toList();
            }
        };
        cache.put("/api/charges", """
                [
                  {"id":"pay-1","name":"Charge payment","type":"HAPPY_PATH","priority":"P0","scenario":"s1",
                   "request":{"method":"POST","path":"/api/payments","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """);
        cache.put("/api/refunds", """
                [
                  {"id":"refund-1","name":"Refund payment","type":"HAPPY_PATH","priority":"P0","scenario":"s2",
                   "request":{"method":"POST","path":"/api/refunds","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":500,"bodyAssertions":{"id":"non-null"}}}
                ]
                """);
        TestGenerationPipeline pipeline = pipeline(List.of(
                endpoint("/api/charges"),
                endpoint("/api/refunds")
        ), cache, contractValidator, "[]");

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> pipeline.generate("openapi: 3.0.0"));

        assertTrue(ex.getOutcome().getAcceptedTestCases().isEmpty());
        assertTrue(ex.getOutcome().getGenerationResults().isEmpty());
        assertEquals(2, ex.getOutcome().getRejectedTestCases().size());
        assertTrue(cache.saved.isEmpty());
    }

    @Test
    void cacheHitContractViolation_isRejectedAndNotExecutedAsAccepted() {
        List<TestCase> cached = new ResponseParser().parse("""
                [
                  {"id":"tc-1","name":"Create payment","type":"HAPPY_PATH","priority":"P0","scenario":"s",
                   "request":{"method":"POST","path":"/api/payments","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """);
        RecordingCache cache = new RecordingCache();
        cache.cachedByFingerprint.put(SpecFingerprint.compute("/api/payments"), cached);
        RecordingContractValidator contractValidator = new RecordingContractValidator(Map.of(
                "tc-1", List.of(contractViolation("tc-1", "METHOD_MISMATCH", "Method 'POST' does not match spec method 'PATCH'"))
        ));
        TestGenerationPipeline pipeline = pipeline(List.of(endpoint("/api/payments")), cache, contractValidator, """
                [
                  {"id":"unused","name":"Unused","type":"HAPPY_PATH","priority":"P0","scenario":"s",
                   "request":{"method":"POST","path":"/api/payments","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> pipeline.generate("openapi: 3.0.0"));

        assertEquals(1, contractValidator.calls);
        assertEquals(0, ex.getOutcome().getAcceptedTestCases().size());
        assertEquals(1, ex.getOutcome().getRejectedTestCases().size());
        assertEquals("tc-1", ex.getOutcome().getRejectedTestCases().get(0).getTestCaseId());
        assertEquals(0, cache.saved.size());
    }

    @Test
    void contractViolationFieldPaths_areDeterministic() {
        assertContractFieldPath("PATH_NOT_FOUND", "testCases[0].request.path");
        assertContractFieldPath("METHOD_MISMATCH", "testCases[0].request.method");
        assertContractFieldPath("FIELD_NOT_IN_SCHEMA", "testCases[0].request.body");
        assertContractFieldPath("STATUS_NOT_IN_SPEC", "testCases[0].expected.status");
    }

    @Test
    void deprecatedRun_failsClosedForContractRejection() {
        RecordingCache cache = new RecordingCache();
        RecordingContractValidator contractValidator = new RecordingContractValidator(Map.of(
                "tc-2", List.of(contractViolation("tc-2", "PATH_NOT_FOUND",
                        "Path '/api/other' does not match spec path '/api/payments'"))
        ));
        TestGenerationPipeline pipeline = pipeline(List.of(endpoint("/api/payments")), cache, contractValidator, """
                [
                  {"id":"tc-1","name":"Create payment","type":"HAPPY_PATH","priority":"P0","scenario":"s1",
                   "request":{"method":"POST","path":"/api/payments","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}},
                  {"id":"tc-2","name":"Create payment variant","type":"HAPPY_PATH","priority":"P0","scenario":"s2",
                   "request":{"method":"POST","path":"/api/other","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> pipeline.run("openapi: 3.0.0"));

        assertEquals(1, ex.getOutcome().getRejectedTestCases().size());
        assertEquals("tc-2", ex.getOutcome().getRejectedTestCases().get(0).getTestCaseId());
    }

    private void assertContractFieldPath(String violationType, String expectedFieldPath) {
        RecordingCache cache = new RecordingCache();
        RecordingContractValidator contractValidator = new RecordingContractValidator(Map.of(
                "tc-1", List.of(contractViolation("tc-1", violationType, "message"))
        ));
        TestGenerationPipeline pipeline = pipeline(List.of(endpoint("/api/payments")), cache, contractValidator, """
                [
                  {"id":"tc-1","name":"Create payment","type":"HAPPY_PATH","priority":"P0","scenario":"s",
                   "request":{"method":"POST","path":"/api/payments","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> pipeline.generate("openapi: 3.0.0"));

        assertEquals(expectedFieldPath,
                ex.getOutcome().getRejectedTestCases().get(0).getValidationIssues().get(0).getFieldPath());
    }

    private ContractViolation contractViolation(String testCaseId, String violationType, String details) {
        return new ContractViolation(testCaseId, "test-case", violationType, details);
    }

    private TestGenerationPipeline pipeline(List<EndpointSpec> endpoints,
                                            RecordingCache cache,
                                            TestCaseContractValidator contractValidator,
                                            String claudeJson) {
        OpenApiLoader loader = yaml -> endpoints;
        EndpointPromptBuilder promptBuilder = endpoint -> endpoint.getPath();
        ClaudeClient claudeClient = prompt -> claudeJson;
        return new TestGenerationPipeline(loader, promptBuilder, claudeClient, new ResponseParser(),
                cache, contractValidator, new com.testforge.ai.validation.TestCaseStructuralValidator());
    }

    private EndpointSpec endpoint(String path) {
        return EndpointSpec.builder()
                .method("POST")
                .path(path)
                .operationId(path.replace("/", "_"))
                .summary("Endpoint " + path)
                .requestBodySchema("{\"properties\":{\"amount\":{}}}")
                .responseSchemas(Map.of("201", "{}"))
                .build();
    }

    private static class RecordingCache implements EndpointCache {
        private final Map<String, List<TestCase>> cachedByFingerprint = new HashMap<>();
        private final List<List<TestCase>> saved = new ArrayList<>();

        @Override
        public Optional<List<TestCase>> findByFingerprint(String fingerprint) {
            return Optional.ofNullable(cachedByFingerprint.get(fingerprint));
        }

        @Override
        public void save(String fingerprint, List<TestCase> testCases) {
            saved.add(List.copyOf(testCases));
            cachedByFingerprint.put(fingerprint, List.copyOf(testCases));
        }

        private void put(String prompt, String json) {
            cachedByFingerprint.put(SpecFingerprint.compute(prompt), new ResponseParser().parse(json));
        }
    }

    private static class RecordingContractValidator extends TestCaseContractValidator {
        private final Map<String, List<ContractViolation>> violationsByTestCaseId;
        private int calls;

        private RecordingContractValidator(Map<String, List<ContractViolation>> violationsByTestCaseId) {
            this.violationsByTestCaseId = violationsByTestCaseId;
        }

        @Override
        public List<ContractViolation> validate(List<TestCase> tests, EndpointSpec spec) {
            calls++;
            List<ContractViolation> violations = new ArrayList<>();
            for (TestCase test : tests) {
                violations.addAll(violationsByTestCaseId.getOrDefault(test.getId(), List.of()));
            }
            return violations;
        }
    }
}
