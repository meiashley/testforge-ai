package com.testforge.ai.pipeline;

import com.testforge.ai.cache.EndpointCache;
import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.parser.ResponseParser;
import com.testforge.ai.prompt.EndpointPromptBuilder;
import com.testforge.ai.validation.ContractViolation;
import com.testforge.ai.validation.RejectedTestCase;
import com.testforge.ai.validation.GenerationValidationException;
import com.testforge.ai.validation.TestCaseContractValidator;
import com.testforge.ai.validation.TestCaseStructuralValidationResult;
import com.testforge.ai.validation.TestCaseStructuralValidator;
import com.testforge.ai.validation.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestGenerationPipelineStructuralValidationTest {

    @Test
    void invalidParsedResultDoesNotEnterContractValidatorOrCache() {
        TrackingContractValidator contractValidator = new TrackingContractValidator();
        TrackingCache cache = new TrackingCache(Optional.empty());
        TestGenerationPipeline pipeline = pipeline("""
                [
                  {"id":"tc-1","name":"Bad","type":"HAPPY_PATH","priority":"P0","scenario":"s",
                   "request":{"method":"TRACE","path":"/api/payments","headers":{},"body":{}},
                   "expected":{"status":201,"bodyAssertions":{}}}
                ]
                """, cache, contractValidator);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> pipeline.run("openapi: 3.0.0"));

        assertNotNull(ex.getOutcome());
        assertTrue(ex.getOutcome().getAcceptedTestCases().isEmpty());
        assertEquals(1, ex.getOutcome().getRejectedTestCases().size());
        assertEquals(0, contractValidator.calls);
        assertEquals(0, cache.saved.size());
    }

    @Test
    void validParsedResultEntersContractValidatorAndAcceptedCache() {
        TrackingContractValidator contractValidator = new TrackingContractValidator();
        TrackingCache cache = new TrackingCache(Optional.empty());
        TestGenerationPipeline pipeline = pipeline(validJson("tc-1", "/api/payments"),
                cache, contractValidator);

        List<GenerationResult> results = pipeline.run("openapi: 3.0.0");

        assertEquals(1, results.size());
        assertEquals(1, results.get(0).getTestCases().size());
        assertEquals(1, contractValidator.calls);
        assertEquals(1, contractValidator.lastValidated.size());
        assertEquals(1, cache.saved.size());
        assertEquals("tc-1", cache.saved.get(0).get(0).getId());
    }

    @Test
    void scenarioWarningOnlyItemRemainsAcceptedAndReachesContractValidation() {
        TrackingContractValidator contractValidator = new TrackingContractValidator();
        TrackingCache cache = new TrackingCache(Optional.empty());
        TestGenerationPipeline pipeline = pipeline("""
                [
                  {"id":"tc-1","name":"Create payment","type":"HAPPY_PATH","priority":"P0","scenario":null,
                   "request":{"method":"POST","path":"/api/payments","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """, cache, contractValidator);

        TestGenerationOutcome outcome = pipeline.generate("openapi: 3.0.0");

        assertEquals(1, outcome.getGenerationResults().size());
        assertEquals(1, outcome.getAcceptedTestCases().size());
        assertEquals(0, outcome.getRejectedTestCases().size());
        assertEquals(1, outcome.getWarnings().size());
        assertEquals("TEST_CASE_SCENARIO_MISSING", outcome.getWarnings().get(0).getCode());
        assertEquals(1, contractValidator.calls);
        assertEquals(1, contractValidator.lastValidated.size());
    }

    @Test
    void cacheHitStillRunsStructuralValidationBeforeContract() {
        List<TestCase> cached = new ResponseParser().parse("""
                [
                  {"id":"tc-cached","name":"Bad cached","type":"HAPPY_PATH","priority":"P0","scenario":"s",
                   "request":{"method":"POST","path":" ","headers":{},"body":{}},
                   "expected":{"status":201,"bodyAssertions":{}}}
                ]
                """);
        TrackingContractValidator contractValidator = new TrackingContractValidator();
        TrackingCache cache = new TrackingCache(Optional.of(cached));
        TestGenerationPipeline pipeline = pipeline(validJson("tc-from-claude", "/api/payments"),
                cache, contractValidator);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> pipeline.run("openapi: 3.0.0"));

        assertNotNull(ex.getOutcome());
        assertTrue(ex.getOutcome().getAcceptedTestCases().isEmpty());
        assertEquals(1, ex.getOutcome().getRejectedTestCases().size());
        assertEquals(0, contractValidator.calls);
        assertEquals(0, cache.saved.size());
    }

    @Test
    void batchStructuralFailure_throwsGenerationValidationExceptionWithOutcome() {
        TrackingContractValidator contractValidator = new TrackingContractValidator();
        TrackingCache cache = new TrackingCache(Optional.empty());
        TestGenerationPipeline pipeline = pipeline("""
                [
                  {"id":"tc-1","name":"Create A","type":"HAPPY_PATH","priority":"P0","scenario":null,
                   "request":{"method":"POST","path":"/api/payments","headers":{},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}},
                  {"id":"tc-1","name":"Create B","type":"HAPPY_PATH","priority":"P0","scenario":"s2",
                   "request":{"method":"POST","path":"/api/payments","headers":{},"body":{"amount":101}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """, cache, contractValidator);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> pipeline.generate("openapi: 3.0.0"));

        TestGenerationOutcome outcome = ex.getOutcome();
        assertNotNull(outcome);
        assertTrue(outcome.getAcceptedTestCases().isEmpty());
        assertTrue(outcome.getGenerationResults().isEmpty());
        assertEquals(1, outcome.getRejectedTestCases().size());
        assertEquals(1, outcome.getRejectedTestCases().get(0).getBatchIndex());
        assertEquals("tc-1", outcome.getRejectedTestCases().get(0).getTestCaseId());
        assertTrue(outcome.getRejectedTestCases().get(0).getValidationIssues().stream()
                .anyMatch(issue -> "DUPLICATE_TEST_CASE_ID".equals(issue.getCode())));
        assertEquals(1, outcome.getWarnings().size());
        assertEquals("TEST_CASE_SCENARIO_MISSING", outcome.getWarnings().get(0).getCode());
        assertEquals(0, contractValidator.calls);
        assertEquals(0, cache.saved.size());
    }

    @Test
    void noStructurallyValidTests_throwsWithCompleteOutcome() {
        TrackingContractValidator contractValidator = new TrackingContractValidator();
        TrackingCache cache = new TrackingCache(Optional.empty());
        TestGenerationPipeline pipeline = pipeline("""
                [
                  {"id":"tc-1","name":"Bad A","type":"HAPPY_PATH","priority":"P0","scenario":null,
                   "request":{"method":"TRACE","path":"/api/payments","headers":{},"body":{}},
                   "expected":{"status":201,"bodyAssertions":{}}},
                  {"id":"tc-2","name":"Bad B","type":"HAPPY_PATH","priority":"P0","scenario":"s2",
                   "request":{"method":"POST","path":" ","headers":{},"body":{}},
                   "expected":{"status":201,"bodyAssertions":{}}}
                ]
                """, cache, contractValidator);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> pipeline.generate("openapi: 3.0.0"));

        TestGenerationOutcome outcome = ex.getOutcome();
        assertNotNull(outcome);
        assertTrue(outcome.getAcceptedTestCases().isEmpty());
        assertTrue(outcome.getGenerationResults().isEmpty());
        assertEquals(2, outcome.getRejectedTestCases().size());
        assertEquals("tc-1", outcome.getRejectedTestCases().get(0).getTestCaseId());
        assertEquals("tc-2", outcome.getRejectedTestCases().get(1).getTestCaseId());
        assertEquals(1, outcome.getWarnings().size());
        assertEquals("TEST_CASE_SCENARIO_MISSING", outcome.getWarnings().get(0).getCode());
        assertEquals(0, contractValidator.calls);
        assertEquals(0, cache.saved.size());
    }

    @Test
    void previousAcceptedEndpoint_isPreservedWhenLaterEndpointFailsStructurally() {
        TrackingContractValidator contractValidator = new TrackingContractValidator();
        TrackingCache cache = new TrackingCache(Optional.empty());
        TestGenerationPipeline pipeline = pipeline(
                List.of(endpoint("/api/payments"), endpoint("/api/refunds")),
                Map.of(
                        "/api/payments", """
                                [
                                  {"id":"tc-1","name":"Create payment","type":"HAPPY_PATH","priority":"P0","scenario":null,
                                   "request":{"method":"POST","path":"/api/payments","headers":{},"body":{"amount":100}},
                                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                                ]
                                """,
                        "/api/refunds", """
                                [
                                  {"id":"tc-2","name":"Bad refund","type":"HAPPY_PATH","priority":"P0","scenario":null,
                                   "request":{"method":"TRACE","path":"/api/refunds","headers":{},"body":{}},
                                   "expected":{"status":201,"bodyAssertions":{}}}
                                ]
                                """
                ),
                cache,
                contractValidator);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> pipeline.generate("openapi: 3.0.0"));

        TestGenerationOutcome outcome = ex.getOutcome();
        assertEquals(1, outcome.getGenerationResults().size());
        assertEquals("/api/payments", outcome.getGenerationResults().get(0).getEndpoint().getPath());
        assertEquals(1, outcome.getAcceptedTestCases().size());
        assertEquals("tc-1", outcome.getAcceptedTestCases().get(0).getId());
        assertEquals(1, outcome.getRejectedTestCases().size());
        assertEquals("tc-2", outcome.getRejectedTestCases().get(0).getTestCaseId());
        assertEquals(2, outcome.getWarnings().size());
        assertTrue(outcome.getWarnings().stream()
                .allMatch(issue -> "TEST_CASE_SCENARIO_MISSING".equals(issue.getCode())));
        assertEquals(1, contractValidator.calls);
        assertEquals(List.of("/api/payments"), contractValidator.validatedEndpointPaths);
        assertEquals(1, cache.saved.size());
        assertEquals("tc-1", cache.saved.get(0).get(0).getId());
    }

    @Test
    void duplicateContentDoesNotReachContractOrCacheForLaterDuplicate() {
        TrackingContractValidator contractValidator = new TrackingContractValidator();
        TrackingCache cache = new TrackingCache(Optional.empty());
        TestGenerationPipeline pipeline = pipeline("""
                [
                  {"id":"tc-1","name":"Create A","type":"HAPPY_PATH","priority":"P0","scenario":"s1",
                   "request":{"method":"POST","path":"/api/payments","headers":{"Content-Type":"application/json"},"body":{"amount":100,"currency":"USD"}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}},
                  {"id":"tc-2","name":"Create B","type":"HAPPY_PATH","priority":"P0","scenario":"s2",
                   "request":{"method":"post","path":"/api/payments","headers":{"content-type":"application/json"},"body":{"currency":"USD","amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """, cache, contractValidator);

        TestGenerationOutcome outcome = pipeline.generate("openapi: 3.0.0");
        List<GenerationResult> results = outcome.getGenerationResults();

        assertEquals(1, results.get(0).getTestCases().size());
        assertEquals("tc-1", results.get(0).getTestCases().get(0).getId());
        assertEquals(1, contractValidator.lastValidated.size());
        assertEquals(1, cache.saved.get(0).size());
        assertTrue(outcome.getWarnings().stream()
                .anyMatch(issue -> "DUPLICATE_EXECUTION_CONTENT".equals(issue.getCode())));
        assertEquals(1, outcome.getRejectedTestCases().size());
        assertEquals(1, outcome.getRejectedTestCases().get(0).getBatchIndex());
        assertEquals("tc-2", outcome.getRejectedTestCases().get(0).getTestCaseId());
    }

    @Test
    void deprecatedRunFailsClosedWhenItemsWereRejected() {
        TrackingContractValidator contractValidator = new TrackingContractValidator();
        TrackingCache cache = new TrackingCache(Optional.empty());
        TestGenerationPipeline pipeline = pipeline("""
                [
                  {"id":"tc-1","name":"Create A","type":"HAPPY_PATH","priority":"P0","scenario":"s1",
                   "request":{"method":"POST","path":"/api/payments","headers":{},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}},
                  {"id":"tc-2","name":"Create B","type":"HAPPY_PATH","priority":"P0","scenario":"s2",
                   "request":{"method":"POST","path":"/api/payments","headers":{},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """, cache, contractValidator);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> pipeline.run("openapi: 3.0.0"));

        assertEquals(1, ex.getOutcome().getRejectedTestCases().size());
        assertEquals("tc-2", ex.getOutcome().getRejectedTestCases().get(0).getTestCaseId());
    }

    @Test
    void deprecatedRun_propagatesStructuralGenerationOutcome() {
        TrackingContractValidator contractValidator = new TrackingContractValidator();
        TrackingCache cache = new TrackingCache(Optional.empty());
        TestGenerationPipeline pipeline = pipeline("""
                [
                  {"id":"tc-1","name":"Bad","type":"HAPPY_PATH","priority":"P0","scenario":null,
                   "request":{"method":"TRACE","path":"/api/payments","headers":{},"body":{}},
                   "expected":{"status":201,"bodyAssertions":{}}}
                ]
                """, cache, contractValidator);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> pipeline.run("openapi: 3.0.0"));

        assertNotNull(ex.getOutcome());
        assertTrue(ex.getOutcome().getAcceptedTestCases().isEmpty());
        assertEquals(1, ex.getOutcome().getRejectedTestCases().size());
        assertEquals("tc-1", ex.getOutcome().getRejectedTestCases().get(0).getTestCaseId());
        assertEquals(1, ex.getOutcome().getWarnings().size());
        assertEquals(0, contractValidator.calls);
        assertEquals(0, cache.saved.size());
    }

    @Test
    void consecutiveGenerateCallsDoNotShareDiagnostics() {
        TrackingContractValidator firstContractValidator = new TrackingContractValidator();
        TestGenerationPipeline first = pipeline("""
                [
                  {"id":"tc-1","name":"Create A","type":"HAPPY_PATH","priority":"P0","scenario":"s1",
                   "request":{"method":"POST","path":"/api/payments","headers":{},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}},
                  {"id":"tc-2","name":"Create B","type":"HAPPY_PATH","priority":"P0","scenario":"s2",
                   "request":{"method":"POST","path":"/api/payments","headers":{},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """, new TrackingCache(Optional.empty()), firstContractValidator);
        TestGenerationOutcome firstOutcome = first.generate("openapi: 3.0.0");

        TrackingContractValidator secondContractValidator = new TrackingContractValidator();
        TestGenerationPipeline second = pipeline(validJson("tc-3", "/api/payments"),
                new TrackingCache(Optional.empty()), secondContractValidator);
        TestGenerationOutcome secondOutcome = second.generate("openapi: 3.0.0");

        assertEquals(1, firstOutcome.getRejectedTestCases().size());
        assertTrue(secondOutcome.getRejectedTestCases().isEmpty());
        assertTrue(secondOutcome.getWarnings().isEmpty());
    }

    @Test
    void outcomeCollectionsAreImmutable() {
        TestGenerationPipeline pipeline = pipeline(validJson("tc-1", "/api/payments"),
                new TrackingCache(Optional.empty()), new TrackingContractValidator());

        TestGenerationOutcome outcome = pipeline.generate("openapi: 3.0.0");

        assertThrows(UnsupportedOperationException.class,
                () -> outcome.getAcceptedTestCases().add(new TestCase()));
        assertThrows(UnsupportedOperationException.class,
                () -> outcome.getRejectedTestCases().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> outcome.getWarnings().clear());
    }

    @Test
    void outcomeDefensivelyCopiesInputCollections() {
        GenerationResult generation = new GenerationResult(endpoint(), List.of(new TestCase()));
        TestCase accepted = new TestCase();
        RejectedTestCase rejected = new RejectedTestCase(1, "tc-2",
                List.of(ValidationIssue.warning("DUPLICATE_EXECUTION_CONTENT", "testCases[1]", "duplicate")));
        ValidationIssue warning = ValidationIssue.warning("TEST_CASE_SCENARIO_MISSING",
                "testCases[0].scenario", "missing scenario");

        List<GenerationResult> generations = new ArrayList<>(List.of(generation));
        List<TestCase> acceptedCases = new ArrayList<>(List.of(accepted));
        List<RejectedTestCase> rejectedCases = new ArrayList<>(List.of(rejected));
        List<ValidationIssue> warnings = new ArrayList<>(List.of(warning));

        TestGenerationOutcome outcome = new TestGenerationOutcome(generations, acceptedCases, rejectedCases, warnings);

        generations.clear();
        acceptedCases.clear();
        rejectedCases.clear();
        warnings.clear();

        assertEquals(1, outcome.getGenerationResults().size());
        assertEquals(1, outcome.getAcceptedTestCases().size());
        assertEquals(1, outcome.getRejectedTestCases().size());
        assertEquals(1, outcome.getWarnings().size());
    }

    @Test
    void validationResultsAreImmutableAndExposeNoSetters() {
        List<TestCase> accepted = new ArrayList<>(List.of(new TestCase()));
        List<ValidationIssue> issues = new ArrayList<>(List.of(
                ValidationIssue.error("REQUEST_REQUIRED", "testCases[0].request", "required")));
        List<RejectedTestCase> rejected = new ArrayList<>(List.of(new RejectedTestCase(0, "tc-1", issues)));

        TestCaseStructuralValidationResult result =
                new TestCaseStructuralValidationResult(accepted, issues, false, rejected);

        accepted.clear();
        issues.clear();
        rejected.clear();

        assertEquals(1, result.getAcceptedTestCases().size());
        assertEquals(1, result.getIssues().size());
        assertEquals(1, result.getRejectedTestCases().size());
        assertThrows(UnsupportedOperationException.class, () -> result.getIssues().clear());
        assertNoSetterMethods(ValidationIssue.class);
        assertNoSetterMethods(TestCaseStructuralValidationResult.class);
    }

    private void assertNoSetterMethods(Class<?> type) {
        assertTrue(Arrays.stream(type.getMethods())
                .noneMatch(method -> method.getName().startsWith("set")),
                type.getSimpleName() + " must not expose setter methods");
    }

    private TestGenerationPipeline pipeline(String claudeJson, TrackingCache cache,
                                            TrackingContractValidator contractValidator) {
        OpenApiLoader loader = yaml -> List.of(endpoint());
        EndpointPromptBuilder promptBuilder = endpoint -> "prompt";
        ClaudeClient claudeClient = prompt -> claudeJson;
        return new TestGenerationPipeline(loader, promptBuilder, claudeClient, new ResponseParser(),
                cache, contractValidator, new TestCaseStructuralValidator());
    }

    private TestGenerationPipeline pipeline(List<EndpointSpec> endpoints, Map<String, String> jsonByPrompt,
                                            TrackingCache cache,
                                            TrackingContractValidator contractValidator) {
        OpenApiLoader loader = yaml -> endpoints;
        EndpointPromptBuilder promptBuilder = EndpointSpec::getPath;
        ClaudeClient claudeClient = jsonByPrompt::get;
        return new TestGenerationPipeline(loader, promptBuilder, claudeClient, new ResponseParser(),
                cache, contractValidator, new TestCaseStructuralValidator());
    }

    private EndpointSpec endpoint() {
        return endpoint("/api/payments");
    }

    private EndpointSpec endpoint(String path) {
        return EndpointSpec.builder()
                .method("POST")
                .path(path)
                .operationId(path.replace("/", "_"))
                .summary("Endpoint " + path)
                .requestBodySchema("{}")
                .responseSchemas(Map.of("201", "{}"))
                .build();
    }

    private String validJson(String id, String path) {
        return """
                [
                  {"id":"%s","name":"Create payment","type":"HAPPY_PATH","priority":"P0","scenario":"s",
                   "request":{"method":"POST","path":"%s","headers":{"Content-Type":"application/json"},"body":{"amount":100}},
                   "expected":{"status":201,"bodyAssertions":{"id":"non-null"}}}
                ]
                """.formatted(id, path);
    }

    private static class TrackingCache implements EndpointCache {
        private final Optional<List<TestCase>> cached;
        private final List<List<TestCase>> saved = new ArrayList<>();

        private TrackingCache(Optional<List<TestCase>> cached) {
            this.cached = cached;
        }

        @Override
        public Optional<List<TestCase>> findByFingerprint(String fingerprint) {
            return cached;
        }

        @Override
        public void save(String fingerprint, List<TestCase> testCases) {
            saved.add(testCases);
        }
    }

    private static class TrackingContractValidator extends TestCaseContractValidator {
        private int calls;
        private List<TestCase> lastValidated = List.of();
        private List<String> validatedEndpointPaths = new ArrayList<>();

        @Override
        public List<ContractViolation> validate(List<TestCase> tests, EndpointSpec spec) {
            calls++;
            lastValidated = tests;
            validatedEndpointPaths.add(spec.getPath());
            return List.of();
        }
    }
}
