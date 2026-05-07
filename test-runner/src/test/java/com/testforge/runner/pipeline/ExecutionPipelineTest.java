package com.testforge.runner.pipeline;

import com.testforge.ai.model.*;
import com.testforge.runner.assertion.AssertionEvaluator;
import com.testforge.runner.http.HttpExecutor;
import com.testforge.runner.model.HttpResponse;
import com.testforge.runner.report.ReportBuilder;
import com.testforge.runner.report.ReportWriter;
import com.testforge.runner.setup.SetupRunner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExecutionPipelineTest {

    private TestCase buildTestCase(String id, String path, Map<String, Object> body, Map<String, Object> assertions) {
        TestCaseRequest request = new TestCaseRequest();
        request.setMethod("POST");
        request.setPath(path);
        request.setBody(body);

        TestCaseExpected expected = new TestCaseExpected();
        expected.setStatus(200);
        expected.setBodyAssertions(assertions);

        TestCase testCase = new TestCase();
        testCase.setId(id);
        testCase.setName("test-" + id);
        testCase.setType(TestCaseType.HAPPY_PATH);
        testCase.setPriority(Priority.P0);
        testCase.setRequest(request);
        testCase.setExpected(expected);
        return testCase;
    }

    @Test
    void substitutesPlaceholdersInPathBodyAndAssertions() {
        Map<String, String> fixtures = Map.of("paymentId", "pay_real123");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("reason", "refund for {{paymentId}}");

        Map<String, Object> assertions = new HashMap<>();
        assertions.put("paymentId", "{{paymentId}}");

        TestCase testCase = buildTestCase("tc-1", "/api/payments/{{paymentId}}/refund", requestBody, assertions);
        GenerationResult generation = new GenerationResult(null, List.of(testCase));

        HttpExecutor httpExecutor = mock(HttpExecutor.class);
        HttpResponse stubResponse = new HttpResponse(200, Map.of(), "", Map.of(), 10L);
        ArgumentCaptor<TestCaseRequest> requestCaptor = ArgumentCaptor.forClass(TestCaseRequest.class);
        when(httpExecutor.execute(requestCaptor.capture(), any())).thenReturn(stubResponse);

        ExecutionPipeline pipeline = new ExecutionPipeline(
                httpExecutor, new AssertionEvaluator(),
                new ReportBuilder(), new ReportWriter("test-placeholder"));

        pipeline.run(List.of(generation), "http://localhost:8080", fixtures);

        TestCaseRequest captured = requestCaptor.getValue();
        assertEquals("/api/payments/pay_real123/refund", captured.getPath());
        assertEquals("refund for pay_real123", captured.getBody().get("reason"));
        assertEquals("pay_real123", testCase.getExpected().getBodyAssertions().get("paymentId"));
    }

    @Test
    void perTestSetupRunnerCalledOnlyForTestCasesWithPlaceholders() {
        SetupRunner setupRunner = mock(SetupRunner.class);
        when(setupRunner.run(any())).thenReturn(Map.of("paymentId", "pay_per_test_456"));

        TestCase withPlaceholder = buildTestCase("tc-with", "/api/payments/{{paymentId}}/refund", null, null);
        TestCase withoutPlaceholder = buildTestCase("tc-without", "/api/payments/list", null, null);
        GenerationResult generation = new GenerationResult(null, List.of(withPlaceholder, withoutPlaceholder));

        HttpExecutor httpExecutor = mock(HttpExecutor.class);
        HttpResponse stubResponse = new HttpResponse(200, Map.of(), "", Map.of(), 5L);
        ArgumentCaptor<TestCaseRequest> requestCaptor = ArgumentCaptor.forClass(TestCaseRequest.class);
        when(httpExecutor.execute(requestCaptor.capture(), any())).thenReturn(stubResponse);

        ExecutionPipeline pipeline = new ExecutionPipeline(
                httpExecutor, new AssertionEvaluator(),
                new ReportBuilder(), new ReportWriter("test-per-test-setup"));

        pipeline.run(List.of(generation), "http://localhost:8080", setupRunner);

        verify(setupRunner, times(1)).run(any());

        List<TestCaseRequest> captured = requestCaptor.getAllValues();
        assertEquals("/api/payments/pay_per_test_456/refund", captured.get(0).getPath());
        assertEquals("/api/payments/list", captured.get(1).getPath());
    }

    @Test
    void emptyFixturesLeavesPathUnchanged() {
        TestCase testCase = buildTestCase("tc-2", "/api/payments/{{paymentId}}", null, null);
        GenerationResult generation = new GenerationResult(null, List.of(testCase));

        HttpExecutor httpExecutor = mock(HttpExecutor.class);
        HttpResponse stubResponse = new HttpResponse(200, Map.of(), "", Map.of(), 5L);
        ArgumentCaptor<TestCaseRequest> requestCaptor = ArgumentCaptor.forClass(TestCaseRequest.class);
        when(httpExecutor.execute(requestCaptor.capture(), any())).thenReturn(stubResponse);

        ExecutionPipeline pipeline = new ExecutionPipeline(
                httpExecutor, new AssertionEvaluator(),
                new ReportBuilder(), new ReportWriter("test-no-fixtures"));

        pipeline.run(List.of(generation), "http://localhost:8080");

        assertEquals("/api/payments/{{paymentId}}", requestCaptor.getValue().getPath());
    }
}
