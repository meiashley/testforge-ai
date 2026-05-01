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

Stored in `PromptBuilder.java` as a constant. Variables: `{endpoint_method}`, `{endpoint_path}`, `{operationId}`, `{request_schema}`, `{response_schemas}`.

**Design principle:** naive baseline. The raw prompt quality reveals real gaps that drive V2 iteration. `docs/prompts.md` updated to V1.1 to reflect JSON output format.

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
