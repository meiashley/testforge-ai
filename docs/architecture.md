# TestForge AI - Architecture

## Overview

TestForge AI is a multi-module Java 21 platform that converts OpenAPI specifications and natural-language requirements into executable API test suites, executes them, and uses AI to diagnose failures.

This document explains the V5 architecture: requirement-driven test generation with consistency checking.

## Module Boundaries

```
testforge-ai/
├── ai-engine/        # AI logic: prompt building, Claude calls, analysis
├── test-runner/      # Test execution + reporting
├── mock-banking-api/ # Reference API under test (Spring Boot)
├── api-gateway/      # REST entry exposing the full pipeline
└── swagger-parser/   # OpenAPI parsing utilities
```

## V5 Quality Pipeline

```
OpenAPI spec + Requirement (markdown)
        │
        ▼
   ┌─────────────────────────┐
   │ RequirementAnalyzer     │ ← AI extracts structured constraints
   └─────────────────────────┘
        │
        ▼
   ┌─────────────────────────┐
   │ ConsistencyChecker      │ ← compare requirement vs OpenAPI
   └─────────────────────────┘
        │
        ▼
   ┌─────────────────────────┐
   │ RequirementApiMapper    │ ← feature → endpoints (lookup)
   └─────────────────────────┘
        │
        ▼
   ┌─────────────────────────┐
   │ ApiFlowResolver         │ ← endpoints → ordered workflow + bindings
   └─────────────────────────┘
        │
        ▼
   ┌─────────────────────────┐
   │ ScenarioPlanner         │ ← add assertions + test data → ExecutionPlan
   └─────────────────────────┘
        │
        ▼
   ┌─────────────────────────┐
   │ ExecutionPipeline       │ ← execute plans against real API
   └─────────────────────────┘
        │
        ▼
   ┌─────────────────────────┐
   │ FailureAnalyzer         │ ← AI diagnoses failed steps
   └─────────────────────────┘
        │
        ▼
   Unified HTML / JSON / MD report
        │
        ▼
   Optional V5 coverage profile enriches the same report paths
   after the test JVM exits and JaCoCo data is generated.
```

## Deterministic Validation Boundaries

TestForge uses deterministic validation gates after LLM deserialization and before runtime execution. These gates reject objects that are syntactically valid JSON but structurally unsafe for downstream code.

### Generated Test-Case Flow

```
LLM raw output
        |
        v
ResponseParser
        |
        v
deterministic structural validation
        |
        v
OpenAPI contract validation
        |
        v
accepted-artifact caching
        |
        v
runtime execution and result validation
```

`ResponseParser` owns raw text cleanup, JSON syntax validation, deserialization, and conversion to the `TestCase` domain model. It does not decide whether a parsed object is executable.

Generated test-case structural validation checks domain-object completeness and executor-safe structure: non-blank ids, batch-unique ids, supported HTTP methods, valid expected status codes, request structure, body assertion map structure, and deterministic duplicate execution content. `scenario` is treated as diagnostic/display metadata; a missing scenario is reported as a warning, not a hard structural error. Structural validation does not validate OpenAPI schema membership.

`TestCaseContractValidator` remains the OpenAPI gate. It checks endpoint method/path alignment, request fields against schema fields, and expected status codes against documented responses. Contract violations are converted into structured `ValidationIssue` errors and `RejectedTestCase` entries in `TestGenerationOutcome`; they do not disappear into logs only.

Runtime execution and result validation remain in `test-runner`: the HTTP request is sent, actual responses are parsed, body assertions are evaluated, and pass/fail/error results are reported.

### V5 Coverage Enrichment Flow

The V5 unified report supports a two-stage JaCoCo integration for the target API module, `mock-banking-api`:

```
V5 test execution
        |
        v
write unified report with coverage=PENDING
        |
        v
test JVM exits and JaCoCo writes test-runner/target/coverage/jacoco-v5.exec
        |
        v
generate test-runner/target/coverage/jacoco/jacoco.xml and full HTML details
        |
        v
parse top-level JaCoCo counters
        |
        v
rewrite v5-execution-report.json/html/md with coverage=AVAILABLE
```

The coverage profile uses a dedicated JaCoCo exec file at `test-runner/target/coverage/jacoco-v5.exec` with append disabled, so V5 coverage is not mixed with `mock-banking-api` unit tests, other test-runner tests, or previous runs. The detailed JaCoCo site is generated under `test-runner/target/coverage/jacoco/`, and the unified report links to it with the relative path `coverage/jacoco/index.html`.

If the coverage profile is not enabled, the report can omit coverage or keep it in a non-available state. The renderer displays `-` for line and branch coverage until JaCoCo XML is available; unavailable or errored coverage is not displayed as `0%`.

### Scenario Execution Flow

```
Resolved flow
        |
        v
ScenarioPlanner
        |
        v
ExecutionPlan
        |
        v
deterministic ExecutionPlan validation
        |
        v
executor
        |
        v
runtime result validation
```

ExecutionPlan validation compares planner output with the original resolved flow before execution. Resolved step fields that the ScenarioPlanner prompt marks as non-responsibilities or that the executor uses as resolved call structure are immutable: `stepId`, `order`, `role`, `method`, `pathTemplate`, `pathBindings`, `headerBindings`, and `outputCapture`. `bodyBinding` is currently not validated because the executor does not consume it and the ScenarioPlanner prompt does not explicitly forbid Planner changes to it. Planner-populated fields are `planId`, `source`, `scenarioId`, `scenarioName`, `metadata.testData`, `requestBody`, `expectedStatusCode`, `assertions`, and `stepDescription`.

Plan validation also checks resolved-flow/plan completeness: step counts must match, every resolved `FlowStep` must appear exactly once in the `ExecutionPlan`, no extra plan step is allowed, and the `ExecutionPlan.steps` list order must match `ResolvedFlow.steps` because the executor runs list order directly. The deprecated `executePlan(ExecutionPlan, String)` entry point fails closed with `RESOLVED_FLOW_REQUIRED`; plan execution requires the source `ResolvedFlow`.

Plan validation also checks step ids and order uniqueness, supported methods, path binding targets, supported output capture sources (`$.statusCode`, `$.body`, `$.body.<field>`, `$.headers.<header>`), supported assertion types, and variable references. `expectedStatusCode` validates the current step response status; `$.statusCode` captures the actual response status for later use. Variables must come from initial inputs, `metadata.testData`, or captures produced by earlier executed steps. Forward references and unresolved placeholders are rejected before any HTTP call is made. Supported HTTP methods are shared by OpenAPI loading and structural validation: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, and `HEAD`; `OPTIONS` is not supported.

Validation field paths use Java-style dot/bracket notation. List items use indexes such as `executionPlan.steps[0]`; map keys use quoted bracket notation with stable escaping, such as `executionPlan.steps[0].pathBindings['order.id']`.

### Failure Semantics

Generated test-case validation is fail-closed:

- JSON syntax or deserialization failure rejects the whole LLM response.
- Duplicate test-case ids are batch-level errors and reject the whole generated batch.
- A structurally invalid individual test case is rejected and excluded from contract validation and execution.
- If no structurally valid test cases remain, generation fails.
- Structural validation failures that fail the generation run are converted at the pipeline boundary into `GenerationValidationException` with the complete `TestGenerationOutcome` accumulated so far. Lower-level `StructuralValidationException` is not allowed to cross into the API gateway.
- Contract-invalid test cases are rejected structurally in the outcome and are excluded from accepted results, accepted cache writes, and execution.
- If a single endpoint produces no contract-valid test cases, no empty `GenerationResult` is created or cached for that endpoint.
- If structural or contract validation leaves the full generation run with no accepted test cases, generation fails closed with the full diagnostic outcome attached.
- Duplicate execution content is reported deterministically and the later duplicate item is rejected without rejecting the whole batch.
- The non-deprecated generation API returns a `TestGenerationOutcome` with immutable collection membership for accepted test cases, rejected test cases, and warnings. It does not deep-copy historical mutable domain objects such as `TestCase`.
- API gateway jobs expose generation diagnostics as additive JSON fields: `generationResults`, `rejectedTestCases`, `validationWarnings`, and `partialAcceptance`. Existing `report`, `status`, and `errorMessage` fields are retained. Gateway execution keeps the full `TestGenerationOutcome` before extracting accepted generation results, so accepted results, rejected items, and warnings remain available through job lookup even when the job fails.

ExecutionPlan validation is plan-level fail-closed:

- Any structural plan error rejects the whole plan.
- The executor does not delete invalid steps or partially execute a plan.
- Validation failures carry stable error codes, Java-style field paths, and human-readable messages.

### Cache Semantics

`RealClaudeClient` caches raw LLM text by model and prompt. That cache is a raw-response cache: any hit still flows through parsing and deterministic validation.

`EndpointCache` stores accepted `TestCase` lists by endpoint prompt fingerprint. A cache miss is saved only after structural validation and OpenAPI contract validation. A cache hit is still revalidated so older invalid cache entries cannot bypass current validators.

There is currently no ExecutionPlan cache.

## Strict Separation of Concerns

### Mapper / Resolver / Planner

These three components must not pollute each other's responsibilities:

| Component               | Responsibility                          | Must NOT do                          |
|-------------------------|----------------------------------------|--------------------------------------|
| `RequirementApiMapper`  | Feature → set of endpoints (lookup)    | Decide call order, add assertions    |
| `ApiFlowResolver`       | Endpoints → ordered flow + bindings    | Add assertions, choose test data     |
| `ScenarioPlanner`       | Flow → assertions + test data + plan   | Re-order steps, change endpoints     |

### Orchestrator

`QualityPipelineOrchestrator` is **pure orchestration**:

- Each method calls one service and updates `QualityContext`
- No `if/else`, no loops, no data transformations
- Business logic stays in services

### QualityContext

A single shared state container preventing "parameter black hole" in the orchestrator. Every pipeline stage reads from and writes to the context, so method signatures stay clean.

## Binding Syntax

Data flows between steps using `${variable}` placeholders with flat namespaced keys.

```
Step 1 outputCapture: { "payment.id": "$.body.id", "payment.statusCode": "$.statusCode" }
        ↓ context["payment.id"] = "pmt_abc"
Step 2 pathBindings:  { "id": "${payment.id}" }
        ↓ /api/payments/{id}/refund → /api/payments/pmt_abc/refund
```

Common namespaces: `payment.*`, `refund.*`, `userA.*`, `userB.*`, `user.*`.

## Report Plugin Pattern

`HtmlReportGenerator` is a plugin coordinator. Each report section implements the `ReportSection` interface and is responsible for its own rendering and `hasContent` decision.

```java
interface ReportSection {
    String render(ExecutionReport report);
    String getSectionId();
    String getNavLabel();
    String getIcon();
    default boolean hasContent(ExecutionReport report) { return true; }
}
```

Sections:
- `SummarySection` (📊)
- `ConsistencySection` (🔍)
- `ScenarioSection` (🎬)
- `ApiSection` (⚡)
- `CoverageSection` (📈)
- `FailureAnalysisSection` (🤖)

Empty sections (e.g. no scenarios in a V4 run) are hidden automatically; the navigation bar only shows sections with content.

## Versions

| Version | Test Cases | Pass Rate | Notable                                         |
|---------|------------|-----------|-------------------------------------------------|
| V1      | 9          | 55.6%     | Naive baseline                                  |
| V3.1    | 9          | 100%      | Per-test lazy fixtures (narrow & reliable)      |
| V4      | 34         | 82.4%     | Dimension-driven generation + AI Failure Analyzer |
| V5      | scenarios  | TBD       | Requirement-driven + consistency check          |

V5 is not a successor to V4: it tests different surface area (multi-step business scenarios). V3.1 and V4 baselines remain valid for their respective scopes.

## Adding a New Pipeline Stage

1. Create a new service class under the appropriate `ai-engine` subpackage
2. Add corresponding field to `QualityContext`
3. Add a `void` method on `QualityPipelineOrchestrator` that calls the service and updates context
4. Add unit tests with mock services
5. Wire the new stage into `analyzeAndPlan()` or expose as a separate orchestrator method

The orchestrator must remain pure — no business logic, no conditional flow.
