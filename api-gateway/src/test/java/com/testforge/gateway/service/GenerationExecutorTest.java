package com.testforge.gateway.service;

import com.testforge.ai.prompt.PromptBuilder;
import com.testforge.ai.prompt.PromptBuilderV2;
import com.testforge.ai.prompt.PromptBuilderV3;
import com.testforge.gateway.job.JobStore;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GenerationExecutorTest {

    private GenerationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new GenerationExecutor(mock(JobStore.class), mock(OkHttpClient.class));
    }

    @Test
    void resolvePromptBuilderReturnsV1() {
        assertInstanceOf(PromptBuilder.class, executor.resolvePromptBuilder("V1"));
    }

    @Test
    void resolvePromptBuilderReturnsV2() {
        assertInstanceOf(PromptBuilderV2.class, executor.resolvePromptBuilder("V2"));
    }

    @Test
    void resolvePromptBuilderReturnsV3ForV3() {
        assertInstanceOf(PromptBuilderV3.class, executor.resolvePromptBuilder("V3"));
    }

    @Test
    void resolvePromptBuilderReturnsV3ForV31() {
        assertInstanceOf(PromptBuilderV3.class, executor.resolvePromptBuilder("V3.1"));
    }

    @Test
    void resolvePromptBuilderThrowsForUnknownVersion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.resolvePromptBuilder("V99"));
        assertTrue(ex.getMessage().contains("V99"));
    }
}
