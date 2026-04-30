# TestForge AI — Prompt Version Registry

All Claude prompts are versioned here for reproducibility and A/B testing.

---

## v1.0 — Test Case Generation (planned)

**Purpose:** Generate BDD Gherkin test cases from an OpenAPI endpoint description.

**Model:** claude-sonnet-4-6

**Template location:** `ai-engine/src/main/java/com/testforge/ai/prompt/TestCasePromptTemplate.java`

**Input variables:**
- `{endpoint_method}` — HTTP method (GET, POST, etc.)
- `{endpoint_path}` — API path pattern
- `{request_schema}` — JSON Schema of the request body
- `{response_schemas}` — JSON Schema map of response codes
- `{constraints}` — Extracted validation constraints

**Prompt (draft):**
```
You are an expert API test engineer. Given the following REST API endpoint specification,
generate comprehensive BDD Gherkin test scenarios covering:
1. Happy path (functional tests)
2. Boundary value analysis
3. Exception / error cases
4. Security edge cases (injection, oversized payloads, etc.)

Endpoint: {endpoint_method} {endpoint_path}

Request Schema:
{request_schema}

Response Schemas:
{response_schemas}

Validation Constraints:
{constraints}

Output ONLY valid Gherkin feature file content. Use concrete example values.
```

---

_Add new prompt versions below as they are developed._
