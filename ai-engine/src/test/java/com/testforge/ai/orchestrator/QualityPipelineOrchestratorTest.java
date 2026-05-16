package com.testforge.ai.orchestrator;

import com.testforge.ai.analysis.FailureAnalysisInput;
import com.testforge.ai.analysis.FailureAnalysisResult;
import com.testforge.ai.analysis.FailureAnalyzer;
import com.testforge.ai.consistency.AlignmentResult;
import com.testforge.ai.consistency.ConsistencyChecker;
import com.testforge.ai.mapping.EndpointMapping;
import com.testforge.ai.mapping.RequirementApiMapper;
import com.testforge.ai.requirement.RequirementAnalysis;
import com.testforge.ai.requirement.RequirementAnalyzer;
import com.testforge.ai.requirement.RequirementConstraint;
import com.testforge.ai.scenario.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QualityPipelineOrchestratorTest {

    // ── stub factories ──────────────────────────────────────────────────────

    private static RequirementAnalysis sampleAnalysis() {
        return RequirementAnalysis.builder()
                .constraints(List.of(RequirementConstraint.builder()
                        .constraintId("req-1").category("VALUE_RANGE")
                        .subject("amount").requirementSection("Section 2.1").build()))
                .identifiedScenarios(List.of("Happy Path", "Refund Flow"))
                .build();
    }

    private static AlignmentResult sampleAlignment() {
        return AlignmentResult.builder()
                .mismatches(List.of()).totalConstraints(1)
                .alignedCount(1).mismatchCount(0)
                .severityBreakdown(Map.of()).categoryBreakdown(Map.of())
                .build();
    }

    private static List<EndpointMapping> sampleMappings() {
        return List.of(EndpointMapping.builder()
                .featureId("feat-1").featureName("Create Payment")
                .requirementSection("Section 2").build());
    }

    private static List<ResolvedFlow> sampleFlows() {
        return List.of(ResolvedFlow.builder()
                .flowId("flow-1").featureId("feat-1")
                .description("Create and verify").build());
    }

    private static List<ExecutionPlan> samplePlans() {
        return List.of(ExecutionPlan.builder()
                .planId("plan-1").source("scenario")
                .scenarioId("sc-1").scenarioName("Happy Path").build());
    }

    // ── call-tracking stubs ─────────────────────────────────────────────────

    static class TrackingAnalyzer extends RequirementAnalyzer {
        final List<String> calls = new ArrayList<>();
        TrackingAnalyzer() { super(prompt -> ""); }
        @Override public RequirementAnalysis analyze(String md) {
            calls.add("analyze:" + md);
            return sampleAnalysis();
        }
    }

    static class TrackingConsistencyChecker extends ConsistencyChecker {
        final List<String> calls = new ArrayList<>();
        TrackingConsistencyChecker() { super(prompt -> ""); }
        @Override public AlignmentResult check(RequirementAnalysis ra, String spec) {
            calls.add("check");
            return sampleAlignment();
        }
    }

    static class TrackingMapper extends RequirementApiMapper {
        final List<String> calls = new ArrayList<>();
        TrackingMapper() { super(prompt -> ""); }
        @Override public List<EndpointMapping> map(RequirementAnalysis ra, String spec) {
            calls.add("map");
            return sampleMappings();
        }
    }

    static class TrackingFlowResolver extends ApiFlowResolver {
        final List<String> calls = new ArrayList<>();
        TrackingFlowResolver() { super(prompt -> ""); }
        @Override public List<ResolvedFlow> resolve(List<EndpointMapping> m, List<String> s, String spec) {
            calls.add("resolve");
            return sampleFlows();
        }
    }

    static class TrackingPlanner extends ScenarioPlanner {
        final List<String> calls = new ArrayList<>();
        TrackingPlanner() { super(prompt -> ""); }
        @Override public List<ExecutionPlan> plan(List<ResolvedFlow> f, RequirementAnalysis ra, String spec) {
            calls.add("plan");
            return samplePlans();
        }
    }

    static class TrackingFailureAnalyzer extends FailureAnalyzer {
        final List<String> calls = new ArrayList<>();
        TrackingFailureAnalyzer() { super(prompt -> ""); }
        @Override public List<FailureAnalysisResult> analyze(List<FailureAnalysisInput> inputs) {
            calls.add("diagnose:" + inputs.size());
            return List.of(FailureAnalysisResult.builder()
                    .testCaseId("tc-1").rootCauseCategory("API_BUG")
                    .confidence("HIGH").build());
        }
    }

    // ── helper to build orchestrator from tracking stubs ────────────────────

    private record Stubs(
            TrackingAnalyzer analyzer,
            TrackingConsistencyChecker checker,
            TrackingMapper mapper,
            TrackingFlowResolver resolver,
            TrackingPlanner planner,
            TrackingFailureAnalyzer failureAnalyzer,
            QualityPipelineOrchestrator orchestrator
    ) {}

    private Stubs stubs() {
        var a = new TrackingAnalyzer();
        var c = new TrackingConsistencyChecker();
        var m = new TrackingMapper();
        var r = new TrackingFlowResolver();
        var p = new TrackingPlanner();
        var f = new TrackingFailureAnalyzer();
        return new Stubs(a, c, m, r, p, f,
                new QualityPipelineOrchestrator(a, c, m, r, p, f));
    }

    private QualityContext baseContext() {
        return QualityContext.builder()
                .requirementContent("# Requirement\n...")
                .openApiSpecContent("openapi: 3.0.0")
                .build();
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void analyzeAndPlan_callsAllStepsAndPopulatesContext() {
        Stubs s = stubs();
        QualityContext ctx = baseContext();

        QualityContext result = s.orchestrator().analyzeAndPlan(ctx);

        // All services called exactly once
        assertEquals(1, s.analyzer().calls.size(), "requirementAnalyzer should be called once");
        assertEquals(1, s.checker().calls.size(), "consistencyChecker should be called once");
        assertEquals(1, s.mapper().calls.size(), "requirementApiMapper should be called once");
        assertEquals(1, s.resolver().calls.size(), "apiFlowResolver should be called once");
        assertEquals(1, s.planner().calls.size(), "scenarioPlanner should be called once");
        assertEquals(0, s.failureAnalyzer().calls.size(), "failureAnalyzer should NOT be called");

        // Context fully populated
        assertNotNull(result.getRequirementAnalysis());
        assertNotNull(result.getConsistencyResult());
        assertFalse(result.getMappings().isEmpty());
        assertFalse(result.getResolvedFlows().isEmpty());
        assertFalse(result.getPlans().isEmpty());

        // Returns same context instance
        assertSame(ctx, result);
    }

    @Test
    void eachMethod_isIndependent_onlyUpdatesItsOwnField() {
        Stubs s = stubs();

        // analyzeRequirement only sets requirementAnalysis
        QualityContext ctx1 = baseContext();
        s.orchestrator().analyzeRequirement(ctx1);
        assertNotNull(ctx1.getRequirementAnalysis());
        assertNull(ctx1.getConsistencyResult());
        assertTrue(ctx1.getMappings().isEmpty());
        assertTrue(ctx1.getResolvedFlows().isEmpty());
        assertTrue(ctx1.getPlans().isEmpty());
        assertEquals(1, s.analyzer().calls.size());
        assertEquals(0, s.checker().calls.size());

        // checkConsistency only sets consistencyResult (requires requirementAnalysis pre-set)
        QualityContext ctx2 = baseContext();
        ctx2.setRequirementAnalysis(sampleAnalysis());
        s.orchestrator().checkConsistency(ctx2);
        assertNotNull(ctx2.getConsistencyResult());
        assertNull(ctx2.getRequirementAnalysis().getMetadata()); // not changed by checker
        assertTrue(ctx2.getMappings().isEmpty());
        assertEquals(1, s.checker().calls.size());
        assertEquals(1, s.analyzer().calls.size()); // not called again
    }

    @Test
    void diagnoseFailures_setsFailureAnalysisOnContext() {
        Stubs s = stubs();
        QualityContext ctx = baseContext();

        List<FailureAnalysisInput> failures = List.of(
                FailureAnalysisInput.builder()
                        .testCaseId("tc-1").testCaseName("create payment")
                        .testCaseType("HAPPY_PATH").endpointMethod("POST")
                        .endpointPath("/api/payments").requestBody("{}")
                        .expectedStatusCode(201).actualStatusCode(400)
                        .actualResponseBody("{}").assertionFailures("").build()
        );

        s.orchestrator().diagnoseFailures(ctx, failures);

        assertEquals(1, s.failureAnalyzer().calls.size());
        assertTrue(s.failureAnalyzer().calls.get(0).contains("1"));
        assertFalse(ctx.getFailureAnalysis().isEmpty());
        assertEquals("API_BUG", ctx.getFailureAnalysis().get(0).getRootCauseCategory());
    }
}
