package com.testforge.ai.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpecFingerprintTest {

    @Test
    void samePromptProducesSameHash() {
        String prompt = "POST /api/payments\nschema: {amount: number}";
        assertEquals(SpecFingerprint.compute(prompt), SpecFingerprint.compute(prompt));
    }

    @Test
    void differentPromptProducesDifferentHash() {
        assertNotEquals(
                SpecFingerprint.compute("POST /api/payments"),
                SpecFingerprint.compute("GET /api/payments/{id}")
        );
    }

    @Test
    void emptyStringProducesHash() {
        String hash = SpecFingerprint.compute("");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void specialCharactersAndUnicodeProduceHash() {
        String prompt = "中文 prompt\n特殊字符: <>&\"'";
        String hash = SpecFingerprint.compute(prompt);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
}
