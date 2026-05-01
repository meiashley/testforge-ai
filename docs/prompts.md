# TestForge AI — Prompt Version Registry

All Claude prompts are versioned here for reproducibility and A/B testing.

---

## v1.1 — Test Case Generation (implemented, baseline)

**Changed from v1.0:** Output format switched from Gherkin to structured JSON array.
Decision rationale: structured TestCase objects enable quality metrics, stable A/B comparison,
and multi-format execution (Gherkin, JUnit, Pytest) from a single source.

**Model:** claude-sonnet-4-5

**Template location:** `ai-engine/src/main/java/com/testforge/ai/prompt/PromptBuilder.java`

**Input variables:**
- `{endpoint_method}` — HTTP method (GET, POST, etc.)
- `{endpoint_path}` — API path pattern
- `{operationId}` — OpenAPI operationId
- `{request_schema}` — JSON Schema of the request body ("N/A" for GET)
- `{response_schemas}` — `statusCode: schema` pairs, one per line

**Prompt:**
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

**Known limitations (V1 naive baseline):**
- No few-shot examples — Claude may vary output structure
- No chain-of-thought — reasoning quality unverified
- No constraint extraction — min/max/pattern values not injected into prompt
- These gaps are intentional: baseline reveals real issues that drive V2 design

---

_Add new prompt versions below as they are developed._
