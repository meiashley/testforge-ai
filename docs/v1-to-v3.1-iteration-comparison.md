# V1 to V3.1 Prompt and Pipeline Iteration

Real Claude API data driving every iteration. No mocks, no synthetic improvements.

## TL;DR

V1   55.6%  -> V2 77.8% (+22.2pp state machine context)
V2   77.8%  -> V3 88.9% (+11.1pp fixture lifecycle)
V3   88.9%  -> V3.1 100% (+11.1pp per-test lazy fixtures)

Total: +44.4pp via 3 isolated changes against real Claude Sonnet 4.5.

## Iteration Summary

| Version | Pass Rate | Key Change | Why It Mattered |
|---------|-----------|------------|-----------------|
| V1 | 55.6% (5/9) | Naive prompt | Baseline - Claude with raw OpenAPI spec |
| V2 | 77.8% (7/9) | + State machine context | Taught Claude API state semantics |
| V3 | 88.9% (8/9) | + Fixture lifecycle | Pre-created seed payment via {{paymentId}} |
| V3.1 | 100% (9/9) | + Per-test lazy fixtures | Fresh seed per test, no state pollution |

## Methodology

Every iteration introduced exactly one variable so pass-rate deltas cleanly attribute to the change made. No confounding factors.

Every baseline was measured against real Anthropic Claude Sonnet 4.5 API output, not mock data. Reports stored under docs/baseline-results-*-real.md.

## V1: Naive Baseline (55.6%)

V1 prompt provides the OpenAPI spec and asks Claude to generate test cases. No business context, no examples.

Failures observed:
- Hardcoded dynamic IDs (e.g. pay_a1b2c3d4e5f6) in HAPPY PATH GET/refund tests
- Wrong status assumptions (expected PENDING, actual COMPLETED)
- LLM-invented assertion DSL (one-of:VALUE1,VALUE2) that the engine did not support

Key insight: V1 failures were information gaps, not Claude's fault. Claude could not predict the API's state machine without being told.

## V2: State Machine Context (77.8%, +22.2pp)

V2 prompt embeds three new context blocks:

1. State machine: 5 explicit transitions (POST creates COMPLETED, full refund creates REFUNDED, etc.)
2. Field guidance: dynamic fields (id, createdAt, updatedAt) use "non-null"; deterministic fields use exact values
3. Few-shot example: ideal HAPPY_PATH test case showing correct status (COMPLETED, not PENDING)

What V2 fixed:
- createPayment status: PENDING -> COMPLETED, all 3 cases now pass
- Field assertion guidance prevents most "invented DSL" issues

What V2 did not fix:
- HAPPY PATH GET/refund still hardcoded payment IDs that did not exist

Key insight: Better prompt without better infrastructure has a ceiling.

## V3: Fixture Lifecycle (88.9%, +11.1pp)

V3 added test fixture infrastructure:

1. SetupRunner: creates a seed payment via POST /api/payments before generation, returns paymentId in a fixtures map
2. Placeholder substitution: ExecutionPipeline replaces {{paymentId}} in test request paths/bodies/assertions with the real seed ID
3. PromptBuilderV3: instructs Claude to use {{paymentId}} for HAPPY PATH GET/refund tests; obviously-fake IDs for negative tests

What V3 fixed:
- HAPPY PATH GET payment: now uses real seed paymentId, returns 200 OK
- HAPPY PATH refund: same mechanism

What V3 did not fix:
- refundPayment BOUNDARY test: full-refund attempt against the seed payment returned 422 because an earlier refund test had already refunded it (state pollution)

Key insight: Shared fixtures cause state pollution between tests.

## V3.1: Per-Test Lazy Fixtures (100%, +11.1pp)

V3.1 changed when fixtures are created, not what they are:

1. ExecutionPipeline scans each test case for {{placeholder}}
2. If found, calls SetupRunner.run() to create a fresh seed payment for that test only
3. If not found, skips setup entirely (no overhead for tests not depending on existing data)

What V3.1 fixed:
- refundPayment BOUNDARY: now gets its own fresh paymentId, refund succeeds, 200 OK

Key insight: Test isolation is a first-class concern in fixture design. Lazy initialization is the right pattern: pay only for what you use.

## Engineering Principles Applied

1. One variable per iteration: Each version changes exactly one thing. Pass-rate deltas attribute cleanly.
2. Real data over mock: All baselines run real Claude API, all reports are reproducible.
3. YAGNI on shared abstractions: V1 V2 V3 V3.1 prompt builders are independent classes; refactor only when third use case appears.
4. Backward compatibility: ExecutionPipeline.run() retains old signatures; V1/V2 tests untouched while V3.1 adds new capabilities.
5. Failure analysis as design input: Each version's remaining failures drove the next version's scope, not arbitrary feature wishlists.

## V4 Roadmap

- Remove the "EXACTLY 3 test cases per endpoint" constraint - let Claude generate as many cases as it deems necessary, measure quality vs cost trade-off
- Multi-fixture support: declarative fixture types (e.g. {{merchantId}}, {{refundedPaymentId}}) instead of hardcoded paymentId
- Fixture teardown: explicit cleanup phase after test execution
- Comparative quality metrics: schema validity score, coverage diversity, bug-detection rate

## Reports

| Version | Real Baseline Report |
|---------|----------------------|
| V1 | docs/baseline-results-v1-real.md |
| V2 | docs/baseline-results-v2-real.md |
| V3 | docs/baseline-results-v3-real.md |
| V3.1 | docs/baseline-results-v3.1-real.md |

