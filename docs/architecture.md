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
```

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
Step 1 outputCapture: { "payment.id": "$.body.id" }
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
