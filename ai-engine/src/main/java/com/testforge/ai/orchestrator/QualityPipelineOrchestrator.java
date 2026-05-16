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
import com.testforge.ai.scenario.ApiFlowResolver;
import com.testforge.ai.scenario.ExecutionPlan;
import com.testforge.ai.scenario.ResolvedFlow;
import com.testforge.ai.scenario.ScenarioPlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * V5 Quality Pipeline Orchestrator.
 *
 * Pure orchestration: each method calls one service and updates QualityContext.
 * No business logic, no if-loop, no mapping/transformation in this class.
 *
 * Each step is independently testable; the orchestrator wires them together.
 */
@Slf4j
@RequiredArgsConstructor
public class QualityPipelineOrchestrator {

    private final RequirementAnalyzer requirementAnalyzer;
    private final ConsistencyChecker consistencyChecker;
    private final RequirementApiMapper requirementApiMapper;
    private final ApiFlowResolver apiFlowResolver;
    private final ScenarioPlanner scenarioPlanner;
    private final FailureAnalyzer failureAnalyzer;

    /**
     * Run V5 quality pipeline up to (but not including) execution.
     * Execution is delegated to test-runner module to keep concerns separated.
     */
    public QualityContext analyzeAndPlan(QualityContext context) {
        analyzeRequirement(context);
        checkConsistency(context);
        mapEndpoints(context);
        resolveFlows(context);
        planScenarios(context);
        return context;
    }

    public void analyzeRequirement(QualityContext context) {
        log.info("[orchestrator] analyzing requirement");
        RequirementAnalysis analysis = requirementAnalyzer.analyze(context.getRequirementContent());
        context.setRequirementAnalysis(analysis);
    }

    public void checkConsistency(QualityContext context) {
        log.info("[orchestrator] checking requirement vs OpenAPI consistency");
        AlignmentResult result = consistencyChecker.check(
                context.getRequirementAnalysis(),
                context.getOpenApiSpecContent()
        );
        context.setConsistencyResult(result);
    }

    public void mapEndpoints(QualityContext context) {
        log.info("[orchestrator] mapping features to endpoints");
        List<EndpointMapping> mappings = requirementApiMapper.map(
                context.getRequirementAnalysis(),
                context.getOpenApiSpecContent()
        );
        context.setMappings(mappings);
    }

    public void resolveFlows(QualityContext context) {
        log.info("[orchestrator] resolving API flows");
        List<ResolvedFlow> flows = apiFlowResolver.resolve(
                context.getMappings(),
                context.getRequirementAnalysis().getIdentifiedScenarios(),
                context.getOpenApiSpecContent()
        );
        context.setResolvedFlows(flows);
    }

    public void planScenarios(QualityContext context) {
        log.info("[orchestrator] planning execution plans with assertions");
        List<ExecutionPlan> plans = scenarioPlanner.plan(
                context.getResolvedFlows(),
                context.getRequirementAnalysis(),
                context.getOpenApiSpecContent()
        );
        context.setPlans(plans);
    }

    public void diagnoseFailures(QualityContext context, List<FailureAnalysisInput> failures) {
        log.info("[orchestrator] diagnosing {} failures", failures.size());
        List<FailureAnalysisResult> diagnoses = failureAnalyzer.analyze(failures);
        context.setFailureAnalysis(diagnoses);
    }
}
