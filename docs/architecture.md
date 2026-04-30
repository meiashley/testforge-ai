# TestForge AI — Architecture

## Implementation Status

This document describes the **target architecture** for TestForge AI.
Current progress is tracked per-module below. See [README.md](../README.md) for the project roadmap.

## Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        api-gateway :8080                     │
│  REST API for test generation requests and result retrieval  │
└────────────┬────────────────────────────┬────────────────────┘
             │                            │
     ┌───────▼───────┐          ┌─────────▼─────────┐
     │  swagger-parser│          │    ai-engine       │
     │  Parses OpenAPI│          │  Claude API client │
     │  spec to domain│          │  Prompt templates  │
     │  model         │          │  Test case builder │
     └───────┬────────┘          └─────────┬──────────┘
             │                             │
             └──────────┬──────────────────┘
                        │
                 ┌──────▼──────┐
                 │    core     │
                 │  Domain     │
                 │  models &   │
                 │  interfaces │
                 └──────┬──────┘
                        │
                 ┌──────▼──────┐
                 │ test-runner │
                 │ JUnit 5 +   │
                 │ Cucumber +  │
                 │ RestAssured │
                 └──────┬──────┘
                        │ HTTP
                 ┌──────▼──────────────┐
                 │  mock-banking-api   │
                 │  :8081              │
                 │  POST /api/payments │
                 │  GET  /api/payments │
                 │  POST /refund       │
                 └─────────────────────┘
```

## Module Responsibilities

| Module | Responsibility | Status |
|--------|---------------|--------|
| `core` | Domain models (OpenApiSpec, TestCase, TestResult), service interfaces | ⏳ Planned |
| `swagger-parser` | Reads OpenAPI 3.0 YAML/JSON, extracts endpoints, schemas, constraints | ⏳ Planned |
| `ai-engine` | Sends prompts to Claude, parses test case responses | ⏳ Planned |
| `test-runner` | Compiles BDD scenarios, executes via RestAssured, reports results | ⏳ Planned |
| `mock-banking-api` | Demo target — exposes payment REST API with OpenAPI 3.0 spec | ✅ Implemented |
| `api-gateway` | Orchestrates the pipeline; persists specs, test cases, and results | ⏳ Planned |

## Data Flow (target)

1. User uploads OpenAPI spec URL or YAML to `api-gateway`
2. `swagger-parser` parses the spec into domain model
3. `ai-engine` generates test cases using Claude
4. `test-runner` executes scenarios against the target API
5. Results stored in PostgreSQL and returned via `api-gateway`

**Current state**: Step 5's target system (`mock-banking-api`) is fully implemented and runnable.
The orchestration pipeline (steps 1-4) is the next phase of development.
