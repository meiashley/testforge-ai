# ai-engine V1 Baseline — Design Spec

**Date:** 2026-05-01  
**Status:** Approved  
**Scope:** Day 5 — minimum closed-loop pipeline (no API billing required)

---

## 1. Goal

Deliver a working, test-verified pipeline that:

1. Reads `mock-banking-api`'s `openapi.yaml`
2. Extracts each endpoint into a structured `EndpointSpec`
3. Builds a V1 naive prompt per endpoint
4. Delegates to `MockClaudeClient` (real API deferred)
5. Parses the JSON response into `List<TestCase>`
6. Prints results to console and writes to `ai-engine/target/v1-output.json`

This is an intentional **baseline** — the naive prompt exposes real weaknesses that drive V2/V3 prompt iteration.

---

## 2. Constraints

| Constraint | Decision |
|---|---|
| No real Claude API calls today | `MockClaudeClient` returns preset JSON |
| V1 prompt is intentionally naive | No few-shot examples, no chain-of-thought |
| Output format | Structured JSON `List<TestCase>` (not Gherkin) |
| Entry point | Pure library — no Spring context; verified via JUnit |
| Domain models | All in `ai-engine` (YAGNI; extract to `core` when `test-runner` defines its needs) |
| Spring removed | `spring-boot-starter` removed from `ai-engine/pom.xml` |
| Pipeline execution | Serial in V1; concurrency is a V2 concern |

---

## 3. Package Structure

```
ai-engine/src/main/java/com/testforge/ai/
├── model/
│   ├── EndpointSpec.java          # Structured endpoint extracted from OpenAPI YAML
│   ├── TestCase.java              # Single generated test case
│   ├── GenerationResult.java      # One endpoint's generation output
│   ├── TestCaseType.java          # Enum: HAPPY_PATH | BOUNDARY | NEGATIVE | SECURITY
│   └── Priority.java              # Enum: P0 | P1 | P2
├── prompt/
│   └── PromptBuilder.java         # EndpointSpec → prompt String (V1 template)
├── client/
│   ├── ClaudeClient.java          # Interface: String generate(String prompt)
│   ├── MockClaudeClient.java      # Returns preset JSON; different response per operationId
│   └── RealClaudeClient.java      # Full HTTP skeleton (OkHttp); throws on execute() with TODO
├── parser/
│   └── ResponseParser.java        # String → List<TestCase> via Jackson
└── pipeline/
    ├── OpenApiLoader.java          # String yamlContent → List<EndpointSpec> via swagger-parser
    └── TestGenerationPipeline.java # Orchestrates the serial pipeline

ai-engine/src/test/java/com/testforge/ai/
├── V1BaselinePipelineTest.java    # Full integration test with real openapi.yaml
├── OpenApiLoaderTest.java         # Unit test with inline YAML strings
├── PromptBuilderTest.java         # Unit test: asserts prompt contains key fields
├── ResponseParserTest.java        # Unit test: inline JSON → List<TestCase>
└── TestFixtures.java              # Helper: loads real openapi.yaml via ${project.basedir}
```

---

## 4. Domain Models

### `EndpointSpec`
```java
// Fields only — all Strings, no constraints extracted (V1 naive)
String method;           // "POST"
String path;             // "/api/payments"
String operationId;      // "createPayment"
String summary;          // from OpenAPI summary field
String requestBodySchema; // JSON Schema serialized to String, null if no request body
Map<String, String> responseSchemas; // statusCode → JSON Schema string
```

### `TestCase`
```java
String id;               // e.g. "tc-001"
String name;             // human-readable test name
TestCaseType type;       // HAPPY_PATH | BOUNDARY | NEGATIVE | SECURITY
Priority priority;       // P0 | P1 | P2
String scenario;         // BDD-style Given/When/Then as a plain string
TestCaseRequest request; // see below
TestCaseExpected expected; // see below
String reasoning;        // AI's explanation for generating this case
```

### `TestCaseRequest`
```java
String method;                    // "POST"
String path;                      // "/api/payments"
Map<String, String> headers;      // e.g. {"Content-Type": "application/json"}
Map<String, Object> body;         // request body as structured map, null for GET
```

### `TestCaseExpected`
```java
int status;                        // expected HTTP status code, e.g. 201
Map<String, Object> bodyAssertions; // field → expected value assertions, may be empty
```

### `GenerationResult`
```java
EndpointSpec endpoint;
List<TestCase> testCases;
```

---

## 5. Key Interfaces & Contracts

### `ClaudeClient`
```java
public interface ClaudeClient {
    String generate(String prompt);  // returns raw JSON string from Claude
}
```

### `MockClaudeClient`
- Holds a `Map<String, String>` of `operationId → presetJson`
- Returns realistic, varied JSON per operationId (createPayment, getPayment, refundPayment)
- Enables independent unit testing of `ResponseParser` and `TestGenerationPipeline`

### `RealClaudeClient`
- Builds full OkHttp request: URL, headers (`x-api-key`, `anthropic-version`, `content-type`), JSON body (model, messages, max_tokens)
- Throws `UnsupportedOperationException` at the `execute()` call site
- Comment at throw site: `// TODO: Remove this line and set ANTHROPIC_API_KEY to enable real API calls`

### `OpenApiLoader`
```java
public interface OpenApiLoader {
    List<EndpointSpec> parse(String yamlContent);
}
// Implementation: SwaggerOpenApiLoader using io.swagger.parser.v3
```

### `PromptBuilder`
- Accepts `EndpointSpec`, fills V1 template variables
- Template is intentionally naive: no few-shot, no CoT
- Output format instruction: `"Output ONLY a valid JSON array of test case objects"`

---

## 6. V1 Prompt Template

Stored in `PromptBuilder.java` as a Java constant. This is the complete template (V1.1):

```
You are an expert API test engineer. Given the following REST API endpoint specification,
generate comprehensive test scenarios covering:
1. Happy path (functional tests)
2. Boundary value analysis
3. Exception / error cases
4. Security edge cases (injection, oversized payloads, etc.)

Endpoint: {endpoint_method} {endpoint_path}
Operation ID: {operationId}

Request Schema:
{request_schema}

Response Schemas:
{response_schemas}

Output ONLY a valid JSON array. Each element must be a test case object with exactly these fields:
{
  "id": "tc-001",
  "name": "short descriptive name",
  "type": "HAPPY_PATH | BOUNDARY | NEGATIVE | SECURITY",
  "priority": "P0 | P1 | P2",
  "scenario": "Given ... When ... Then ...",
  "request": {
    "method": "POST",
    "path": "/api/payments",
    "headers": {"Content-Type": "application/json"},
    "body": {}
  },
  "expected": {
    "status": 201,
    "bodyAssertions": {"id": "non-null"}
  },
  "reasoning": "why this test case is important"
}

Do not include any explanation, markdown, or text outside the JSON array.
```

**Design principle:** naive baseline — no few-shot examples, no chain-of-thought, no constraint extraction. Raw quality reveals real gaps that drive V2 iteration.

**Variable substitution note:** For endpoints with no request body (e.g. GET), `{request_schema}` is substituted with `"N/A"`. `PromptBuilder` handles this via a null-check on `EndpointSpec.requestBodySchema`.

`docs/prompts.md` will be updated to V1.1 to record the switch from Gherkin to JSON output.

---

## 7. Data Flow

```
openapi.yaml (String)
    │
    ▼
OpenApiLoader.parse()
    │ List<EndpointSpec> (3 endpoints)
    ▼
for each EndpointSpec:
    PromptBuilder.build(endpointSpec)
        │ prompt String
        ▼
    ClaudeClient.generate(prompt)          ← MockClaudeClient in V1
        │ raw JSON String
        ▼
    ResponseParser.parse(json)
        │ List<TestCase>
        ▼
    GenerationResult(endpointSpec, testCases)
    │
    ▼
List<GenerationResult>
    │
    ├── console: Jackson pretty-print
    └── file: ai-engine/target/v1-output.json
```

---

## 8. Pipeline Entry Point

`TestGenerationPipeline` is a plain Java class (no Spring), constructed with:
```java
new TestGenerationPipeline(openApiLoader, promptBuilder, claudeClient, responseParser)
```

Method:
```java
List<GenerationResult> run(String yamlContent);
```

---

## 9. Test Strategy

| Test class | Type | What it verifies |
|---|---|---|
| `V1BaselinePipelineTest` | Integration | Full pipeline with real openapi.yaml → asserts 3 `GenerationResult`s, each with ≥1 `TestCase`, all required fields non-null |
| `OpenApiLoaderTest` | Unit | Inline YAML → correct `EndpointSpec` count and field values; malformed YAML → `IllegalArgumentException` with descriptive message |
| `PromptBuilderTest` | Unit | Generated prompt contains `method`, `path`, `operationId`, schema content |
| `ResponseParserTest` | Unit | Valid inline JSON → `List<TestCase>`; malformed JSON → clear exception |

`TestFixtures.loadMockBankingApiSpec()` reads the real file using the `project.basedir` system property injected by Maven Surefire. Works identically from IDE and CI.

---

## 10. pom.xml Changes (ai-engine)

**Remove:**
- `spring-boot-starter`

**Add:**
- `io.swagger.parser.v3:swagger-parser` (version managed by parent pom)
- `org.junit.jupiter:junit-jupiter` (test scope)

**Keep:**
- `jackson-databind`, `okhttp`, `lombok`

---

## 11. Output Artifacts

- Console: pretty-printed JSON array of `GenerationResult`
- `ai-engine/target/v1-output.json`: same content, written by test; gitignored

---

## 12. Explicitly Out of Scope (V1)

- Real Claude API calls
- V2/V3 prompt improvements (few-shot, CoT, constraint extraction)
- Concurrent/batch endpoint processing
- `core` module changes
- `swagger-parser` module (separate concern)
- Persistence, API gateway integration
- Quality metrics (schema validity, coverage, diversity)

---

## 13. Success Criteria (V1 Acceptance Checklist)

- [ ] `mvn test -pl ai-engine` passes with zero failures
- [ ] `V1BaselinePipelineTest` produces exactly 3 `GenerationResult`s (one per endpoint)
- [ ] Each `GenerationResult` contains ≥ 3 `TestCase` objects
- [ ] All `TestCase` required fields (`id`, `name`, `type`, `scenario`, `request`, `expected`) are non-null
- [ ] Console output is pretty-printed JSON, human-readable
- [ ] `ai-engine/target/v1-output.json` exists and is valid JSON after test run
- [ ] `docs/prompts.md` updated to V1.1 with note: "switched output format from Gherkin to JSON"
- [ ] `spring-boot-starter` removed from `ai-engine/pom.xml`

---

## Appendix A: Mock Response Examples

These are the preset JSON strings returned by `MockClaudeClient`. Keyed by `operationId`. Also serve as inputs for `ResponseParserTest`.

### `createPayment` (POST /api/payments)

```json
[
  {
    "id": "tc-001",
    "name": "Create payment with valid CNY amount",
    "type": "HAPPY_PATH",
    "priority": "P0",
    "scenario": "Given a valid payment request with amount 128.50 CNY\nWhen POST /api/payments is called\nThen response status is 201\nAnd response body contains a non-null payment id\nAnd status is COMPLETED",
    "request": {
      "method": "POST",
      "path": "/api/payments",
      "headers": {"Content-Type": "application/json"},
      "body": {"merchantId": "merchant-001", "customerId": "customer-abc", "amount": 128.50, "currency": "CNY"}
    },
    "expected": {
      "status": 201,
      "bodyAssertions": {"id": "non-null", "status": "COMPLETED"}
    },
    "reasoning": "Validates the core happy path for payment creation with all required fields."
  },
  {
    "id": "tc-002",
    "name": "Create payment with minimum allowed amount",
    "type": "BOUNDARY",
    "priority": "P1",
    "scenario": "Given a payment request with amount 0.01 (minimum)\nWhen POST /api/payments is called\nThen response status is 201",
    "request": {
      "method": "POST",
      "path": "/api/payments",
      "headers": {"Content-Type": "application/json"},
      "body": {"merchantId": "merchant-001", "customerId": "customer-abc", "amount": 0.01, "currency": "USD"}
    },
    "expected": {
      "status": 201,
      "bodyAssertions": {"status": "COMPLETED"}
    },
    "reasoning": "Boundary: minimum valid amount per schema (minimum: 0.01)."
  },
  {
    "id": "tc-003",
    "name": "Create payment with zero amount is rejected",
    "type": "NEGATIVE",
    "priority": "P0",
    "scenario": "Given a payment request with amount 0\nWhen POST /api/payments is called\nThen response status is 400\nAnd error code is VALIDATION_ERROR",
    "request": {
      "method": "POST",
      "path": "/api/payments",
      "headers": {"Content-Type": "application/json"},
      "body": {"merchantId": "merchant-001", "customerId": "customer-abc", "amount": 0, "currency": "USD"}
    },
    "expected": {
      "status": 400,
      "bodyAssertions": {"code": "VALIDATION_ERROR"}
    },
    "reasoning": "Amount must be > 0 per schema; zero should be rejected with 400."
  }
]
```

### `getPayment` (GET /api/payments/{id})

```json
[
  {
    "id": "tc-004",
    "name": "Get existing payment by valid ID",
    "type": "HAPPY_PATH",
    "priority": "P0",
    "scenario": "Given a payment with id pay_a1b2c3d4e5f6 exists\nWhen GET /api/payments/pay_a1b2c3d4e5f6 is called\nThen response status is 200\nAnd response body contains the payment details",
    "request": {
      "method": "GET",
      "path": "/api/payments/pay_a1b2c3d4e5f6",
      "headers": {},
      "body": null
    },
    "expected": {
      "status": 200,
      "bodyAssertions": {"id": "pay_a1b2c3d4e5f6", "status": "non-null"}
    },
    "reasoning": "Core happy path: retrieve a payment that exists."
  },
  {
    "id": "tc-005",
    "name": "Get payment with non-existent ID returns 404",
    "type": "NEGATIVE",
    "priority": "P0",
    "scenario": "Given no payment with id pay_doesnotexist exists\nWhen GET /api/payments/pay_doesnotexist is called\nThen response status is 404",
    "request": {
      "method": "GET",
      "path": "/api/payments/pay_doesnotexist",
      "headers": {},
      "body": null
    },
    "expected": {
      "status": 404,
      "bodyAssertions": {}
    },
    "reasoning": "Standard not-found case per OpenAPI 404 response spec."
  },
  {
    "id": "tc-006",
    "name": "Get payment with SQL injection in ID",
    "type": "SECURITY",
    "priority": "P1",
    "scenario": "Given an ID containing SQL injection payload\nWhen GET /api/payments/1' OR '1'='1 is called\nThen response status is 404 or 400\nAnd no stack trace is exposed in response body",
    "request": {
      "method": "GET",
      "path": "/api/payments/1' OR '1'='1",
      "headers": {},
      "body": null
    },
    "expected": {
      "status": 404,
      "bodyAssertions": {}
    },
    "reasoning": "Security: ensure injection payloads in path parameters are handled safely."
  }
]
```

### `refundPayment` (POST /api/payments/{id}/refund)

```json
[
  {
    "id": "tc-007",
    "name": "Full refund of a completed payment",
    "type": "HAPPY_PATH",
    "priority": "P0",
    "scenario": "Given a COMPLETED payment with id pay_a1b2c3d4e5f6\nWhen POST /api/payments/pay_a1b2c3d4e5f6/refund with reason only\nThen response status is 200\nAnd payment status is REFUNDED",
    "request": {
      "method": "POST",
      "path": "/api/payments/pay_a1b2c3d4e5f6/refund",
      "headers": {"Content-Type": "application/json"},
      "body": {"reason": "Customer requested cancellation"}
    },
    "expected": {
      "status": 200,
      "bodyAssertions": {"status": "REFUNDED"}
    },
    "reasoning": "Core happy path: full refund omitting optional amount field."
  },
  {
    "id": "tc-008",
    "name": "Partial refund with valid amount",
    "type": "HAPPY_PATH",
    "priority": "P1",
    "scenario": "Given a COMPLETED payment of 128.50\nWhen POST /api/payments/{id}/refund with amount 50.00\nThen response status is 200\nAnd status is PARTIALLY_REFUNDED",
    "request": {
      "method": "POST",
      "path": "/api/payments/pay_a1b2c3d4e5f6/refund",
      "headers": {"Content-Type": "application/json"},
      "body": {"amount": 50.00, "reason": "Partial order cancellation"}
    },
    "expected": {
      "status": 200,
      "bodyAssertions": {"status": "PARTIALLY_REFUNDED"}
    },
    "reasoning": "Partial refund happy path."
  },
  {
    "id": "tc-009",
    "name": "Refund a non-refundable payment returns 422",
    "type": "NEGATIVE",
    "priority": "P0",
    "scenario": "Given a payment in REFUNDED state\nWhen POST /api/payments/{id}/refund is called\nThen response status is 422",
    "request": {
      "method": "POST",
      "path": "/api/payments/pay_already_refunded/refund",
      "headers": {"Content-Type": "application/json"},
      "body": {"reason": "double refund attempt"}
    },
    "expected": {
      "status": 422,
      "bodyAssertions": {}
    },
    "reasoning": "Business rule: only COMPLETED payments can be refunded; 422 for invalid state transitions."
  }
]
```
