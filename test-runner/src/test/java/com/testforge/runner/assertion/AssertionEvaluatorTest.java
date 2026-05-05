package com.testforge.runner.assertion;

import com.testforge.runner.model.AssertionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AssertionEvaluatorTest {

    private final AssertionEvaluator evaluator = new AssertionEvaluator();

    @Test
    void nonNull_passes_whenFieldHasValue() {
        Map<String, Object> assertions = Map.of("id", "non-null");
        Map<String, Object> body = Map.of("id", "pay_123");

        List<AssertionResult> results = evaluator.evaluate(assertions, body);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isPassed());
        assertNull(results.get(0).getFailureReason());
    }

    @Test
    void nonNull_fails_whenFieldIsNull() {
        Map<String, Object> assertions = Map.of("id", "non-null");
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", null);

        List<AssertionResult> results = evaluator.evaluate(assertions, body);

        assertEquals(1, results.size());
        assertFalse(results.get(0).isPassed());
        assertEquals("expected non-null but was null", results.get(0).getFailureReason());
    }

    @Test
    void nonNull_fails_whenFieldAbsent() {
        Map<String, Object> assertions = Map.of("id", "non-null");
        Map<String, Object> body = Map.of("status", "COMPLETED");

        List<AssertionResult> results = evaluator.evaluate(assertions, body);

        assertEquals(1, results.size());
        assertFalse(results.get(0).isPassed());
        assertEquals("field absent", results.get(0).getFailureReason());
    }

    @Test
    void literalString_passes_whenEqual() {
        Map<String, Object> assertions = Map.of("status", "COMPLETED");
        Map<String, Object> body = Map.of("status", "COMPLETED");

        List<AssertionResult> results = evaluator.evaluate(assertions, body);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isPassed());
    }

    @Test
    void literalString_fails_whenNotEqual() {
        Map<String, Object> assertions = Map.of("status", "COMPLETED");
        Map<String, Object> body = Map.of("status", "PENDING");

        List<AssertionResult> results = evaluator.evaluate(assertions, body);

        assertEquals(1, results.size());
        assertFalse(results.get(0).isPassed());
        assertEquals("expected COMPLETED but was PENDING", results.get(0).getFailureReason());
    }

    @Test
    void literalNumber_passes_whenEqual() {
        Map<String, Object> assertions = Map.of("amount", 100);
        Map<String, Object> body = Map.of("amount", 100);

        List<AssertionResult> results = evaluator.evaluate(assertions, body);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isPassed());
    }

    @Test
    void typeMismatch_fails() {
        Map<String, Object> assertions = Map.of("amount", "100");
        Map<String, Object> body = Map.of("amount", 100);

        List<AssertionResult> results = evaluator.evaluate(assertions, body);

        assertEquals(1, results.size());
        assertFalse(results.get(0).isPassed());
        assertEquals("type mismatch: expected String but was Integer", results.get(0).getFailureReason());
    }

    @Test
    void absentField_fails() {
        Map<String, Object> assertions = Map.of("currency", "CNY");
        Map<String, Object> body = Map.of("status", "COMPLETED");

        List<AssertionResult> results = evaluator.evaluate(assertions, body);

        assertEquals(1, results.size());
        assertFalse(results.get(0).isPassed());
        assertEquals("field absent", results.get(0).getFailureReason());
    }

    @Test
    void multipleAssertions_allPass() {
        Map<String, Object> assertions = Map.of("id", "non-null", "status", "COMPLETED");
        Map<String, Object> body = Map.of("id", "pay_123", "status", "COMPLETED");

        List<AssertionResult> results = evaluator.evaluate(assertions, body);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(AssertionResult::isPassed));
    }

    @Test
    void multipleAssertions_partialFail() {
        Map<String, Object> assertions = Map.of("id", "non-null", "status", "COMPLETED");
        Map<String, Object> body = Map.of("id", "pay_123", "status", "PENDING");

        List<AssertionResult> results = evaluator.evaluate(assertions, body);

        assertEquals(2, results.size());
        long passedCount = results.stream().filter(AssertionResult::isPassed).count();
        long failedCount = results.stream().filter(r -> !r.isPassed()).count();
        assertEquals(1, passedCount);
        assertEquals(1, failedCount);
    }

    @Test
    void emptyAssertions_returnsEmptyList() {
        List<AssertionResult> results = evaluator.evaluate(Map.of(), Map.of("id", "pay_123"));

        assertTrue(results.isEmpty());
    }
}
