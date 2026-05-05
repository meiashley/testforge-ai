# test-runner V1 Implementation Plan

**Spec:** docs/superpowers/specs/2026-05-04-test-runner-v1-design.md

**Goal:** Build a test-verified execution pipeline that takes ai-engine output, executes test cases against live mock-banking-api, and produces structured reports.

**Tech Stack:** Java 21, Maven, JUnit 5, RestAssured/OkHttp, Jackson, WireMock, Spring Boot Test (integration only), Lombok.

---

## Task 1: pom.xml + .gitignore

- [ ] Replace test-runner/pom.xml per spec Section 9 (remove spring-boot-starter and Cucumber; add ai-engine compile scope, mock-banking-api test scope, jackson-databind, okhttp, wiremock-jre8 test scope, spring-boot-starter-test test scope; keep core, junit-jupiter, rest-assured, lombok; add Surefire plugin injecting project.basedir)
- [ ] Add .gitignore entries: test-runner/target/v1-execution-report.json and test-runner/target/v1-execution-report.md
- [ ] Run: mvn compile -pl test-runner (expect BUILD SUCCESS)
- [ ] Commit: chore(test-runner): replace deps - add ai-engine, mock-banking-api, jackson, wiremock

---

## Task 2: Domain models

Create under test-runner/src/main/java/com/testforge/runner/model/ per spec Section 4:

- [ ] TestResultStatus.java (enum: PASSED, FAILED, ERROR)
- [ ] HttpResponse.java (@Value: statusCode, body Map, rawBody, headers Map, durationMs)
- [ ] AssertionResult.java (@Value: field, expected, actual, passed, failureReason)
- [ ] TestCaseResult.java (@Value: testCaseId, name, type, priority, status, httpResponse, assertionResults, failureCategory, durationMs)
- [ ] ExecutionSummary.java (@Value: total, passed, failed, errored, passRate, executedAt, totalDurationMs)
- [ ] ExecutionReport.java (@Value: summary, results)
- [ ] Run: mvn compile -pl test-runner
- [ ] Commit: feat(test-runner): add domain model classes for execution results

---

## Task 3: AssertionEvaluator (RED to GREEN)

- [ ] Write AssertionEvaluatorTest with 11 cases per spec Section 10:
  - non-null passes when field has value
  - non-null fails when field is null
  - non-null fails when field absent
  - literal string equal passes
  - literal string not equal fails
  - literal number equal passes
  - type mismatch fails
  - absent field fails
  - multiple assertions all pass
  - multiple assertions partial fail (1 fails out of 2)
  - empty assertions returns empty list
- [ ] Create empty AssertionEvaluator skeleton that throws UnsupportedOperationException
- [ ] Run tests, confirm 11 RED
- [ ] Implement AssertionEvaluator per spec Section 5 DSL rules
- [ ] Run tests, confirm 11 GREEN
- [ ] Commit: feat(test-runner): implement AssertionEvaluator with 11 unit tests

---

## Task 4: HttpExecutor (RED to GREEN)

- [ ] Write HttpExecutorTest using WireMock:
  - successful JSON response parsed into HttpResponse with body Map
  - non-JSON response sets rawBody and null body
  - (optional) network error throws descriptive exception
- [ ] Create empty HttpExecutor skeleton
- [ ] Run tests, confirm RED
- [ ] Implement HttpExecutor per spec Section 5 (OkHttp + Jackson, parse JSON, fallback to rawBody, track durationMs)
- [ ] Run tests, confirm GREEN
- [ ] Commit: feat(test-runner): implement HttpExecutor with WireMock unit tests

---

## Task 5: ReportBuilder

- [ ] Implement ReportBuilder per spec Section 5 and Section 6:
  - Take List of TestCaseResult and metadata, produce ExecutionReport
  - Apply failure categorization heuristic (5 rules from spec Section 6, first match wins)
  - Compute summary stats (total, passed, failed, errored, passRate, executedAt, totalDurationMs)
- [ ] Run: mvn compile -pl test-runner
- [ ] Commit: feat(test-runner): implement ReportBuilder with failure categorization

---

## Task 6: ReportWriter

- [ ] Implement ReportWriter per spec Section 5 and Section 8:
  - Console: summary banner + per-FAIL one-liners (printed to stdout)
  - JSON: write target/v1-execution-report.json (Jackson pretty-print)
  - Markdown: write target/v1-execution-report.md with 4 sections (summary table grouped by endpoint, failure categories, representative failures, V2 prompt improvement targets)
- [ ] Run: mvn compile -pl test-runner
- [ ] Commit: feat(test-runner): implement ReportWriter for console/JSON/Markdown output

---

## Task 7: ExecutionPipeline

- [ ] Implement ExecutionPipeline orchestrator per spec Section 5:
  - Accept List of GenerationResult and baseUrl
  - For each TestCase: call HttpExecutor, compare status code, call AssertionEvaluator, build TestCaseResult
  - Determine status: PASSED if status matches AND all body assertions pass; FAILED if any assertion fails; ERROR if exception thrown
  - Pass List of TestCaseResult to ReportBuilder, then ReportWriter
  - Return ExecutionReport
- [ ] Run: mvn compile -pl test-runner
- [ ] Commit: feat(test-runner): implement ExecutionPipeline orchestrator

---

## Task 8: V1ExecutionPipelineTest (integration)

- [ ] Write V1ExecutionPipelineTest per spec Section 11:
  - @SpringBootTest(classes = MockBankingApiApplication.class, webEnvironment = RANDOM_PORT)
  - @LocalServerPort int port
  - Build ai-engine pipeline manually
  - Load openapi.yaml via TestFixtures pattern (use ${project.basedir} system property)
  - Run ai-engine pipeline to get List of GenerationResult
  - Run test-runner ExecutionPipeline
  - Assertions: total == 9, passed >= 1, failed >= 1 (V1 baseline must have failures), JSON file exists, Markdown file exists, JSON parses back to ExecutionReport
- [ ] Run: mvn test -pl test-runner -Dtest=V1ExecutionPipelineTest
- [ ] Verify report files in test-runner/target/
- [ ] Commit: test(test-runner): add V1ExecutionPipelineTest - full pipeline integration

---

## Task 9: Final verification

- [ ] Run full test suite: mvn test -pl test-runner (all tests GREEN)
- [ ] Verify all spec Section 13 success criteria checkboxes
- [ ] Cat test-runner/target/v1-execution-report.md to review baseline results
- [ ] Manually copy representative report to docs/baseline-results-v1.md (commit this one)
- [ ] Final commit if needed: chore(test-runner): V1 baseline complete - X passed Y failed Z errors
