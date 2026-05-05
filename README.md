# TestForge AI

AI-powered API test generation platform. Java 21 + Spring Boot + Anthropic Claude.

## What It Does

Reads an OpenAPI 3.0 specification, generates structured test cases using Anthropic Claude, then executes them against the live API and produces structured execution reports.

Pipeline: OpenAPI YAML → AI Engine (Claude) → List of TestCases → Test Runner → ExecutionReport (JSON + Markdown)

## V1 Baseline Results

Pass rate: 55.6% (5/9) — see docs/baseline-results-v1.md

V1 uses an intentionally naive prompt to establish a baseline. Failure patterns:
- 3 cases: Resource not found (likely hardcoded id)
- 1 case: Hardcoded dynamic value

These patterns drive V2 prompt iteration (documented in the report).

## Architecture

| Module | Status | Description |
|--------|--------|-------------|
| mock-banking-api | V1 | 3-endpoint payment service (the test target) |
| ai-engine | V1 | OpenAPI to Claude prompt to structured TestCases |
| test-runner | V1 | Executes TestCases, evaluates assertions, generates reports |
| api-gateway | Planned | REST entry point for the full pipeline |

## Testing

- ai-engine: 12 tests (TDD throughout)
- test-runner: 18 tests (11 unit + 2 WireMock + 4 categorization + 1 integration)
- mock-banking-api: 5 controller tests
- Total: 35 tests passing

## Quick Start

git clone https://github.com/meiashley/testforge-ai.git
cd testforge-ai
mvn install -DskipTests
mvn test -pl test-runner -Dtest=V1ExecutionPipelineTest
cat test-runner/target/v1-execution-report.md

## Tech Stack

- Language: Java 21
- Framework: Spring Boot 3.2 (mock service only; library code is plain Java)
- AI: Anthropic Claude API (Sonnet 4.5)
- Test: JUnit 5, RestAssured, WireMock, Spring Boot Test
- Build: Maven 3 (multi-module)
- Other: Jackson, OkHttp, Lombok, swagger-parser

## Engineering Practices

- TDD throughout — every feature has tests written before implementation (RED-GREEN-REFACTOR)
- Spec-driven development — design spec, then implementation plan, then execute
- Conventional commits — type(scope): subject format
- Strategy Pattern for AI client — MockClaudeClient and RealClaudeClient are interchangeable
- YAGNI on shared abstractions — domain models stay in their owning module until cross-module need is proven

## Roadmap

- [ ] V2 prompt iteration (target 85%+ pass rate)
- [ ] Real Claude API integration (currently MockClaudeClient backed)
- [ ] api-gateway REST entry point
- [ ] Migrate integration tests to Testcontainers
- [ ] Quality metrics: schema validity, coverage, diversity

## Design Documents

- ai-engine V1 Spec: docs/superpowers/specs/2026-05-01-ai-engine-v1-design.md
- test-runner V1 Spec: docs/superpowers/specs/2026-05-04-test-runner-v1-design.md
- V1 Baseline Results: docs/baseline-results-v1.md
- Architecture: docs/architecture.md

## License

MIT