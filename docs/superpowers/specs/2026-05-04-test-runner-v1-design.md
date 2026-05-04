# test-runner V1 — Design Spec

**Date:** 2026-05-04
**Status:** Approved
**Scope:** Day 6 — execute ai-engine V1 output against live mock-banking-api, produce structured execution reports

---

## 1. Goal

Deliver a working, test-verified execution pipeline that:

1. Accepts `List<GenerationResult>` from ai-engine (the 9 test cases generated in Day 5)
2. Sends each `TestCase.request` as a real HTTP call to a running mock-banking-api instance
3. Evaluates `TestCase.expected` assertions against the actual response
4. Generates three output artifacts: console summary, JSON report, Markdown report
5. Is fully self-contained: `mvn test -pl test-runner` starts mock-banking-api internally and runs the full pipeline

This is a **V1 baseline** — some test cases are intentionally expected to FAIL (hardcoded dynamic values from V1 naive prompt). The failure report is the primary deliverable: it provides data for V2 prompt improvement.

---

## 2. Constraints

| Constraint | Decision |
|---|---|
| API server startup | `@SpringBootTest(webEnvironment = RANDOM_PORT)` embeds mock-banking-api |
| ai-engine integration | `ai-engine` dependency at `test` scope; `List<GenerationResult>` passed as Java objects |
| mock-banking-api dependency | `mock-banking-api` at `test` scope |
| bodyAssertions DSL | V1: only `"non-null"` (existence check) and literal equality; all other values treated as strict `equals` |
| Pipeline execution | Serial in V1 |
| Spring in production code | None — test-runner main code is a pure Java library |
| Report files | Not committed to git; `target/v1-execution-report.{json,md}` in `.gitignore` |
| V1 baseline failures | Expected and intentional — hardcoded dynamic ids in mock data will FAIL |

---

## 3. Package Structure

```
test-runner/src/main/java/com/testforge/runner/
├── model/
│   ├── TestResultStatus.java      # enum: PASSED / FAILED / ERROR
│   ├── HttpResponse.java          # value object: statusCode + body(Map) + rawBody + headers + durationMs
│   ├── AssertionResult.java       # single assertion outcome: field / expected / actual / passed / failureReason
│   ├── TestCaseResult.java        # single test case execution: status + assertionResults + httpResponse + durationMs
│   ├── ExecutionSummary.java      # aggregate: total/passed/failed/errored/passRate/executedAt/durationMs
│   └── ExecutionReport.java       # top-level: summary + List<TestCaseResult>
├── assertion/
│   └── AssertionEvaluator.java    # Map<String,Object> assertions + Map<String,Object> actualBody → List<AssertionResult>
├── http/
│   └── HttpExecutor.java          # TestCaseRequest + baseUrl → HttpResponse
├── report/
│   ├── ReportBuilder.java         # List<TestCaseResult> + metadata → ExecutionReport (includes failure categorization)
│   └── ReportWriter.java          # ExecutionReport → console + JSON file + Markdown file
└── pipeline/
    └── ExecutionPipeline.java     # orchestrates: List<GenerationResult> + baseUrl → ExecutionReport

test-runner/src/test/java/com/testforge/runner/
├── assertion/
│   └── AssertionEvaluatorTest.java   # 11 unit tests (pure logic, no network)
├── http/
│   └── HttpExecutorTest.java          # WireMock unit tests (no Spring)
└── V1ExecutionPipelineTest.java       # integration test (@SpringBootTest + full chain)
```

---

## 4. Domain Models

### `TestResultStatus`
```java
enum TestResultStatus { PASSED, FAILED, ERROR }
```

### `HttpResponse`
```java
@Value
public class HttpResponse {
    int statusCode;
    Map<String, Object> body;    // parsed JSON; null if response is not JSON
    String rawBody;              // always populated; used for debug and non-JSON responses
    Map<String, String> headers;
    long durationMs;
}
```

### `AssertionResult`
```java
@Value
public class AssertionResult {
    String field;
    Object expected;
    Object actual;       // null if field was absent in response
    boolean passed;
    String failureReason; // null when passed; human-readable when failed
}
```
`failureReason` values: `"field absent"`, `"expected non-null but was null"`, `"expected <X> but was <Y>"`, `"type mismatch: expected String but was Integer"`

### `TestCaseResult`
```java
@Value
public class TestCaseResult {
    String testCaseId;
    String name;
    TestCaseType type;       // from ai-engine
    Priority priority;
    TestResultStatus status;
    HttpResponse httpResponse;
    List<AssertionResult> assertionResults;
    String failureCategory;  // null when PASSED; set by ReportBuilder heuristic
    long durationMs;
}
```

### `ExecutionSummary`
```java
@Value
public class ExecutionSummary {
    int total;
    int passed;
    int failed;
    int errored;
    double passRate;         // passed / total, 0.0–1.0
    String executedAt;       // ISO-8601
    long totalDurationMs;
}
```

### `ExecutionReport`
```java
@Value
public class ExecutionReport {
    ExecutionSummary summary;
    List<TestCaseResult> results;
}
```

---

## 5. Key Interfaces & Contracts

### `AssertionEvaluator.evaluate()`
```java
// Returns one AssertionResult per entry in bodyAssertions.
// Empty bodyAssertions → returns empty list (counts as PASSED for body checks).
List<AssertionResult> evaluate(Map<String, Object> bodyAssertions, Map<String, Object> actualBody)
```

**DSL rules (V1):**
- `"non-null"` → field must exist in actualBody AND not be null
- Any other String/Number/Boolean → strict `Objects.equals(expected, actual)`
- Field absent in actualBody → FAIL with reason `"field absent"`
- `actual` is a different Java type than `expected` → FAIL with reason `"type mismatch: expected <T1> but was <T2>"`
- `bodyAssertions` is null or empty → return empty list (no body assertions)

### `HttpExecutor.execute()`
```java
HttpResponse execute(TestCaseRequest request, String baseUrl)
```
- Builds full URL: `baseUrl + request.getPath()`
- Sets all headers from `request.getHeaders()`
- Sets body from `request.getBody()` (serialized to JSON) for non-GET
- Attempts to parse response body as JSON into `Map<String, Object>`; on parse failure, sets `body = null`, `rawBody = raw string`
- On network error or timeout: throws `RuntimeException` with cause (caller maps to ERROR status)

### `ExecutionPipeline.run()`
```java
ExecutionReport run(List<GenerationResult> generationResults, String baseUrl)
```
For each `TestCase`:
1. Call `HttpExecutor.execute(testCase.getRequest(), baseUrl)` → `HttpResponse`
2. Compare `httpResponse.getStatusCode()` vs `testCase.getExpected().getStatus()` → status assertion
3. Call `AssertionEvaluator.evaluate(testCase.getExpected().getBodyAssertions(), httpResponse.getBody())` → body assertions
4. Determine `TestResultStatus`: PASSED if status matches AND all body assertions pass; FAILED if any assertion fails; ERROR if exception thrown
5. Collect into `List<TestCaseResult>`
6. Pass to `ReportBuilder` → `ExecutionReport`
7. Pass to `ReportWriter` → console + files

### `ReportBuilder.build()`
```java
ExecutionReport build(List<TestCaseResult> results)
```
Computes summary stats and applies `failureCategory` heuristic to each FAILED result.

### `ReportWriter.write()`
```java
void write(ExecutionReport report, Path outputDir)
```
Writes three artifacts atomically to `outputDir`:
1. Console: summary banner + per-FAIL one-liner
2. `v1-execution-report.json`: full Jackson pretty-print of `ExecutionReport`
3. `v1-execution-report.md`: summary table + Failure Categories + V2 Prompt Improvement Targets

---

## 6. Failure Categorization Heuristic (V1)

Applied by `ReportBuilder` to each FAILED `TestCaseResult`. Rules evaluated in order, first match wins:

| Condition | Category |
|---|---|
| Expected body field value starts with `"pay_"` | `"Hardcoded dynamic value"` |
| Expected status 200/201 but actual 404 | `"Resource not found (likely hardcoded id)"` |
| Expected status 4xx but actual 2xx | `"Validation expectation incorrect"` |
| `AssertionResult.failureReason` contains `"type mismatch"` | `"Type mismatch"` |
| None of the above | `"Uncategorized"` |

---

## 7. Data Flow

```
List<GenerationResult>  (from ai-engine pipeline)
    │
    ▼
ExecutionPipeline.run(results, baseUrl)
    │
    ├── for each TestCase:
    │       HttpExecutor.execute(request, baseUrl)
    │           │ HttpResponse
    │           ▼
    │       status assertion (int comparison)
    │       AssertionEvaluator.evaluate(bodyAssertions, httpResponse.body)
    │           │ List<AssertionResult>
    │           ▼
    │       TestCaseResult (PASSED / FAILED / ERROR)
    │
    ▼
List<TestCaseResult>
    │
    ▼
ReportBuilder.build(results)
    │  applies failure category heuristic
    ▼
ExecutionReport
    │
    ▼
ReportWriter.write(report, outputDir)
    ├── console: summary banner + FAIL one-liners
    ├── target/v1-execution-report.json
    └── target/v1-execution-report.md
```

---

## 8. Report Formats

### Console output
```
===== V1 EXECUTION REPORT =====
Total: 9 | Passed: 4 | Failed: 5 | Errors: 0 | Pass Rate: 44.4%
Duration: 1243ms | Executed: 2026-05-04T10:00:00Z

FAILURES:
  [tc-005] NEGATIVE  | Get payment with non-existent ID returns 404     | status: expected 404 but was 404 → body: field 'id' expected 'pay_doesnotexist' but was absent
  [tc-006] SECURITY  | Get payment with SQL injection in ID              | status: expected 404 but was 404 → body: field absent
  ...

Reports written to:
  test-runner/target/v1-execution-report.json
  test-runner/target/v1-execution-report.md
================================
```

### JSON report structure
```json
{
  "summary": {
    "total": 9,
    "passed": 4,
    "failed": 5,
    "errored": 0,
    "passRate": 0.444,
    "executedAt": "2026-05-04T10:00:00Z",
    "totalDurationMs": 1243
  },
  "results": [
    {
      "testCaseId": "tc-001",
      "name": "Create payment with valid CNY amount",
      "type": "HAPPY_PATH",
      "priority": "P0",
      "status": "PASSED",
      "httpResponse": {
        "statusCode": 201,
        "body": { "id": "pay_xyz", "status": "COMPLETED" },
        "rawBody": "...",
        "headers": { "Content-Type": "application/json" },
        "durationMs": 45
      },
      "assertionResults": [
        { "field": "id", "expected": "non-null", "actual": "pay_xyz", "passed": true, "failureReason": null },
        { "field": "status", "expected": "COMPLETED", "actual": "COMPLETED", "passed": true, "failureReason": null }
      ],
      "failureCategory": null,
      "durationMs": 45
    }
  ]
}
```

### Markdown report sections
1. **Summary table** — grouped by endpoint (`createPayment` / `getPayment` / `refundPayment`), shows pass/fail counts
2. **Failure Categories** — counts per category from heuristic
3. **Representative Failures** — 2–3 concrete examples with expected vs actual
4. **V2 Prompt Improvement Targets** — inferred from failure patterns, e.g. "Instruct Claude to use `non-null` for all dynamic id fields"

---

## 9. pom.xml Changes (test-runner)

**Remove:**
- `spring-boot-starter` (main code has no Spring)
- `cucumber-java`, `cucumber-junit-platform-engine`, `cucumber-spring` (not used in V1)

**Add:**
- `com.testforge:ai-engine` (test scope)
- `com.testforge:mock-banking-api` (test scope)
- `com.fasterxml.jackson.core:jackson-databind` (report serialization)
- `com.squareup.okhttp3:okhttp` (HTTP execution in HttpExecutor)
- `com.github.tomakehurst:wiremock-jre8` (HttpExecutorTest, test scope)
- `org.springframework.boot:spring-boot-starter-test` (test scope; includes spring-boot-test, Mockito, JsonPath)
- Surefire plugin config: inject `project.basedir` (identical to ai-engine/pom.xml)

**Keep:**
- `core`, `junit-jupiter`, `lombok`, `rest-assured`

---

## 10. Test Strategy

| Test class | Type | What it verifies |
|---|---|---|
| `AssertionEvaluatorTest` | Unit | 11 cases: non-null pass/fail, literal equality, absent field, type mismatch, empty assertions, multi-assertion |
| `HttpExecutorTest` | Unit (WireMock) | successful JSON parse → `HttpResponse`; non-JSON response → `rawBody` set, `body` null; network error → descriptive exception |
| `V1ExecutionPipelineTest` | Integration | Full chain: Spring Boot mock-banking-api + ai-engine pipeline → `ExecutionReport`; asserts total=9, ≥1 PASSED, ≥1 FAILED, JSON/MD files written |

`V1ExecutionPipelineTest` does **not** hardcode expected pass/fail counts beyond ≥1 each. V1 baseline results are allowed to vary as ai-engine prompts evolve.

---

## 11. V1ExecutionPipelineTest Setup

```java
@SpringBootTest(
    classes = MockBankingApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class V1ExecutionPipelineTest {

    @LocalServerPort
    int port;

    @Test
    void runsFullPipelineAndProducesReport() throws IOException {
        // 1. Build ai-engine pipeline
        TestGenerationPipeline aiPipeline = new TestGenerationPipeline(
            new SwaggerOpenApiLoader(), new PromptBuilder(),
            new MockClaudeClient(), new ResponseParser());

        // 2. Load openapi.yaml and generate test cases
        String yaml = TestFixtures.loadMockBankingApiSpec();  // same helper pattern as ai-engine
        List<GenerationResult> generationResults = aiPipeline.run(yaml);

        // 3. Execute against live server
        String baseUrl = "http://localhost:" + port;
        ExecutionReport report = new ExecutionPipeline(
            new HttpExecutor(), new AssertionEvaluator(),
            new ReportBuilder(), new ReportWriter()
        ).run(generationResults, baseUrl);

        // 4. Assertions
        assertEquals(9, report.getSummary().getTotal());
        assertTrue(report.getSummary().getPassed() >= 1,  "at least 1 test must PASS");
        assertTrue(report.getSummary().getFailed() >= 1,  "at least 1 test must FAIL (V1 baseline)");

        // 5. File artifacts
        Path outputDir = Path.of(System.getProperty("project.basedir", "."), "target");
        assertTrue(Files.exists(outputDir.resolve("v1-execution-report.json")));
        assertTrue(Files.exists(outputDir.resolve("v1-execution-report.md")));
        // JSON must be parseable
        assertDoesNotThrow(() ->
            new ObjectMapper().readValue(
                outputDir.resolve("v1-execution-report.json").toFile(), ExecutionReport.class));
    }
}
```

---

## 12. .gitignore Additions

```
# test-runner V1 execution reports (generated, not tracked)
test-runner/target/v1-execution-report.json
test-runner/target/v1-execution-report.md
```

Manually copy representative reports to `docs/baseline-results-v1.md` for public visibility.

---

## 13. Success Criteria (V1 Acceptance Checklist)

- [ ] `mvn test -pl test-runner` passes (all tests GREEN, including the expected baseline failures)
- [ ] `V1ExecutionPipelineTest` reports `total == 9`, `passed >= 1`, `failed >= 1`
- [ ] `AssertionEvaluatorTest` all 11 unit tests GREEN
- [ ] `HttpExecutorTest` all WireMock tests GREEN
- [ ] `test-runner/target/v1-execution-report.json` written and valid JSON
- [ ] `test-runner/target/v1-execution-report.md` written with all 4 sections
- [ ] Console output shows summary banner and per-FAIL one-liners
- [ ] Cucumber dependencies removed from `test-runner/pom.xml`
- [ ] No Spring context in production code (only in test)

---

## 14. Explicitly Out of Scope (V1)

- Cucumber / BDD runner (removed from pom.xml)
- Parallel test execution
- Retry logic for flaky tests
- HTML / Allure reports
- Authentication headers
- Test case filtering by type or priority
- `core` module extraction (deferred until api-gateway)

---

## 15. V2 Roadmap (from this brainstorm)

| Item | Trigger |
|---|---|
| Migrate to Testcontainers | After mock-banking-api is Dockerized |
| Extract `TestCase`, `GenerationResult` to `core` | After api-gateway defines its own needs |
| Extended bodyAssertions DSL: `"any-string"`, `"any-number"`, `"matches:<regex>"` | After V2 prompt is updated to use them |
| `ReportWriter` interface + multiple implementations (HTML, Allure) | When reporting becomes a first-class concern |
| Parallel execution | After baseline stability confirmed |
| Filter execution by `TestCase.priority` (P0-only fast run) | After CI pipeline is set up |
