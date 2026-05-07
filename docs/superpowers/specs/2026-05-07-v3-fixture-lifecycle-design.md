# V3 Test Fixture Lifecycle - Design Spec

**Date:** 2026-05-07
**Status:** Approved
**Goal:** Fix V2's remaining 2 failures by adding test fixture setup before execution.

## Problem (from V2 baseline)

V2 reached 77.8% pass rate. Remaining 2 failures:
- GET /api/payments/{id} HAPPY PATH: hardcoded ID `pay_a1b2c3d4e5f6` doesn't exist → 404
- POST /api/payments/{id}/refund HAPPY PATH: same hardcoded ID issue

Root cause: pipeline lacks setup-teardown lifecycle. HAPPY PATH tests targeting existing data have no way to declare "I need a real payment first."

## Solution: Setup Hook + Placeholder Substitution

### 1. TestFixtures (Map<String, String>)
A simple key-value store of test data references. Example:
- `paymentId` → "pay_xxx" (real ID from a prior POST)

### 2. SetupRunner
Before generation:
- Executes a hardcoded list of setup actions (POST requests against the live API)
- Extracts response fields into TestFixtures map
- For V3: just one setup action — POST /api/payments → extract response.id into fixtures.paymentId

### 3. Placeholder Substitution in test cases
- V3 prompt instructs Claude to use {{paymentId}} placeholder for existing payment references
- ExecutionPipeline replaces {{paymentId}} -> fixtures.paymentId before HTTP execution

## Components

### SetupRunner (test-runner module)
- Method: run(String baseUrl) returns Map<String, String>
- For V3 hardcoded: creates 1 payment, returns {"paymentId": "pay_xxx"}

### ExecutionPipeline (modified)
- New constructor parameter: Map<String, String> fixtures (or empty map)
- Before each test execution: substitute {{key}} placeholders in:
  - request.path
  - request.body (if any)
  - expected.bodyAssertions

### PromptBuilderV3 (ai-engine module)
Extends V2 prompt with placeholder guidance:
- "For HAPPY PATH GET/refund tests targeting existing payments, use {{paymentId}} as path placeholder"
- Few-shot example showing placeholder usage

## V3 Pipeline Flow

1. Start mock-banking-api (@SpringBootTest)
2. SetupRunner.run() returns POST /payments result -> fixtures = {"paymentId": "pay_xxx"}
3. ai-engine generates test cases (Claude uses {{paymentId}} placeholders)
4. ExecutionPipeline substitutes {{paymentId}} -> "pay_xxx" in all test cases
5. Execute HTTP requests with real IDs
6. Assert results

## Test Strategy

- SetupRunnerTest: 1 unit test, verifies it returns fixtures map
- ExecutionPipelineTest: extend with placeholder substitution test (1 case)
- V3BaselinePipelineTest: integration test, expected pass rate >= 90%

## Success Criteria

- mvn test -pl test-runner all GREEN
- V3BaselinePipelineTest produces total >= 9, passed >= 8 (~90%+)
- v3-execution-report.{json,md} generated
- V1, V2, V3 tests all coexist independently

## Out of Scope (V4 roadmap)

- Multiple fixtures (just paymentId for V3)
- Teardown/cleanup of fixture data
- Dynamic fixture dependencies
- Removal of "EXACTLY 3 test cases" constraint -> V4 unconstrained generation
- Parallel test execution

