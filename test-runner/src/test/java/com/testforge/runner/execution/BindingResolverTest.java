package com.testforge.runner.execution;

import com.testforge.ai.scenario.StepDataContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BindingResolverTest {

    private final BindingResolver resolver = new BindingResolver();

    private StepDataContext ctx(String... keyValues) {
        StepDataContext ctx = new StepDataContext();
        for (int i = 0; i < keyValues.length; i += 2) {
            ctx.put(keyValues[i], keyValues[i + 1]);
        }
        return ctx;
    }

    @Test
    void resolve_simpleVar() {
        StepDataContext ctx = ctx("user.token", "abc");
        assertEquals("abc", resolver.resolve("${user.token}", ctx));
    }

    @Test
    void resolve_inText() {
        StepDataContext ctx = ctx("user.token", "abc");
        assertEquals("Bearer abc", resolver.resolve("Bearer ${user.token}", ctx));
    }

    @Test
    void resolve_multipleVars() {
        StepDataContext ctx = ctx("a", "valA", "b", "valB");
        assertEquals("valA-valB", resolver.resolve("${a}-${b}", ctx));
    }

    @Test
    void resolve_missingVar_preservesPlaceholder() {
        StepDataContext ctx = new StepDataContext();
        assertEquals("${unknown}", resolver.resolve("${unknown}", ctx));
    }

    @Test
    void resolve_nullTemplate_returnsNull() {
        StepDataContext ctx = new StepDataContext();
        assertNull(resolver.resolve(null, ctx));
    }

    @Test
    void resolveMap_allValuesResolved() {
        StepDataContext ctx = ctx("user.token", "tok-xyz", "payment.id", "pay-123");
        Map<String, String> input = Map.of(
                "Authorization", "Bearer ${user.token}",
                "X-Payment-Id", "${payment.id}"
        );
        Map<String, String> result = resolver.resolveMap(input, ctx);
        assertEquals("Bearer tok-xyz", result.get("Authorization"));
        assertEquals("pay-123", result.get("X-Payment-Id"));
    }
}
