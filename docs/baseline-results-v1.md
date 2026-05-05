# V1 Execution Report

Executed: 2026-05-05T08:16:02.855114Z | Duration: 239ms

## Summary

| Endpoint | Total | Passed | Failed |
|----------|-------|--------|--------|
| createPayment | 3 | 3 | 0 |
| getPayment | 3 | 2 | 1 |
| refundPayment | 3 | 0 | 3 |
| **Total** | **9** | **5** | **4** |

## Failure Categories

| Category | Count |
|----------|-------|
| Resource not found (likely hardcoded id) | 3 |
| Hardcoded dynamic value | 1 |

## Representative Failures

### [tc-004] Get existing payment by valid ID (HAPPY_PATH)
- **Category**: Hardcoded dynamic value
- **Actual status**: 404
- **Assertion results**:
  - `id`: expected `pay_a1b2c3d4e5f6` → actual `null` ✗
  - `status`: expected `non-null` → actual `404` ✓

### [tc-007] Full refund of a completed payment (HAPPY_PATH)
- **Category**: Resource not found (likely hardcoded id)
- **Actual status**: 404
- **Assertion results**:
  - `status`: expected `REFUNDED` → actual `404` ✗

### [tc-008] Partial refund with valid amount (HAPPY_PATH)
- **Category**: Resource not found (likely hardcoded id)
- **Actual status**: 404
- **Assertion results**:
  - `status`: expected `PARTIALLY_REFUNDED` → actual `404` ✗

## V2 Prompt Improvement Targets

Based on V1 baseline failure patterns:

- **Hardcoded dynamic IDs**: Instruct Claude to use `non-null` for all dynamic id fields (e.g. `id`, `paymentId`) instead of hardcoding `pay_*` literals.
- **Hardcoded path IDs**: Instruct Claude to use a known seed payment ID returned by a prior create call, or use `non-null` for GET assertions.
