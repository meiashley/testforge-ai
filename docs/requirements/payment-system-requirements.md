# Payment System Requirements

> Product Requirements Document — Payment Service for E-commerce Platform
> Version 1.0 | Owner: Product Team | Status: Approved for Implementation

## 1. Overview

The payment service supports an e-commerce platform's payment, refund, and query operations. The system handles multi-user scenarios where a payer initiates payment and may later request refund. All payments and refunds must be auditable, idempotent, and authorization-protected.

## 2. Payment Creation

### 2.1 Amount Constraints
- Amount must be between **AUD 1.00 and AUD 100,000.00** (inclusive)
- Amounts outside this range must be rejected with HTTP 400 and error code `INVALID_AMOUNT`
- Amount precision: 2 decimal places; additional precision is truncated

### 2.2 Currency Support
- Supported currencies: **AUD, USD, EUR**
- Unsupported currency must be rejected with HTTP 400 and error code `UNSUPPORTED_CURRENCY`

### 2.3 Required Fields
- `amount` (decimal): payment amount
- `currency` (string): three-letter ISO 4217 code
- `payerId` (string): authenticated user ID of the payer

### 2.4 Response on Success
- HTTP 201 Created
- Body must contain: `id`, `amount`, `currency`, `payerId`, `status`, `createdAt`
- Initial `status` must be `PENDING` or `COMPLETED` (depending on payment method)

## 3. Payment State Machine

### 3.1 Allowed Transitions
- `PENDING` → `COMPLETED` (payment authorized)
- `COMPLETED` → `REFUNDED` (refund issued)

### 3.2 Forbidden Transitions
- `PENDING` → `REFUNDED` (cannot refund unauthorized payment)
- Any state → `PENDING` (no rollback to pending)
- `REFUNDED` is a **terminal state**: no further state changes permitted

### 3.3 Status Query
- `GET /api/payments/{id}` returns current status
- Status must reflect the latest state machine transition

## 4. Refund Rules

### 4.1 Eligibility
- Only payments in `COMPLETED` status can be refunded
- Attempting to refund a `PENDING` payment must return HTTP 422 with error code `INVALID_STATE`

### 4.2 Idempotency
- A payment can be refunded **at most once**
- Re-attempting refund on a `REFUNDED` payment must return HTTP 422 with error code `ALREADY_REFUNDED`

### 4.3 Ownership Constraint
- Only the original `payerId` can initiate refund for their own payment
- Cross-user refund attempts (User B refunding User A's payment) must return HTTP 403 with error code `FORBIDDEN_OWNERSHIP`

### 4.4 Refund Amount
- Refund must match the original payment amount exactly
- Partial refunds are **not supported** in v1.0

## 5. Authorization

### 5.1 Authentication
- All endpoints require a valid user session (Bearer token in `Authorization` header)
- Missing or invalid token must return HTTP 401 with error code `UNAUTHORIZED`

### 5.2 Authorization Rules
- `POST /api/payments`: any authenticated user
- `GET /api/payments/{id}`: must be the original payer or admin
- `POST /api/payments/{id}/refund`: must be the original payer (admin override not permitted in v1.0)

## 6. Business Scenarios for Testing

The following scenarios represent end-to-end business flows that must be validated:

### 6.1 Happy Path: Create and Query
A user creates a payment and queries it. The query returns the expected status and full payment details.

### 6.2 Successful Refund Flow
A user creates a payment, the payment completes, the user refunds it, and a subsequent status query confirms `REFUNDED` state.

### 6.3 Cross-User Refund Attempt (Should Be Rejected)
User A creates a payment. User B attempts to refund A's payment. The refund must be rejected with `FORBIDDEN_OWNERSHIP`.

### 6.4 Double Refund Attempt (Should Be Rejected)
A user refunds a payment successfully. The same user attempts to refund the same payment again. The second refund must be rejected with `ALREADY_REFUNDED`.

### 6.5 Refund Non-Existing Payment
A user attempts to refund a payment ID that does not exist. The system must return HTTP 404 with error code `PAYMENT_NOT_FOUND`.

### 6.6 Refund Pending Payment (Should Be Rejected)
A user attempts to refund a payment still in `PENDING` state. The system must return HTTP 422 with `INVALID_STATE`.

## 7. Error Response Format

All error responses must follow this structure:

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable description",
  "timestamp": "ISO 8601 datetime"
}
```

## 8. Performance Requirements

- Payment creation: p95 latency < 200ms
- Status query: p95 latency < 50ms
- Refund: p95 latency < 300ms
