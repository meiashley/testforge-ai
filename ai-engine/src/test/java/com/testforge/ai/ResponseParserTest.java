package com.testforge.ai;

import com.testforge.ai.model.Priority;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.model.TestCaseType;
import com.testforge.ai.parser.ResponseParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResponseParserTest {

    private ResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new ResponseParser();
    }

    @Test
    void parsesValidJsonArrayIntoTestCases() {
        String json = """
                [
                  {
                    "id": "tc-001",
                    "name": "Happy path payment",
                    "type": "HAPPY_PATH",
                    "priority": "P0",
                    "scenario": "Given a valid request When POST /api/payments Then 201",
                    "request": {
                      "method": "POST",
                      "path": "/api/payments",
                      "headers": {"Content-Type": "application/json"},
                      "body": {"amount": 100.0, "currency": "CNY"}
                    },
                    "expected": {
                      "status": 201,
                      "bodyAssertions": {"id": "non-null", "status": "COMPLETED"}
                    },
                    "reasoning": "Core happy path"
                  }
                ]
                """;

        List<TestCase> testCases = parser.parse(json);

        assertEquals(1, testCases.size());
        TestCase tc = testCases.get(0);
        assertEquals("tc-001", tc.getId());
        assertEquals("Happy path payment", tc.getName());
        assertEquals(TestCaseType.HAPPY_PATH, tc.getType());
        assertEquals(Priority.P0, tc.getPriority());
        assertNotNull(tc.getScenario());
        assertNotNull(tc.getRequest());
        assertEquals("POST", tc.getRequest().getMethod());
        assertEquals("/api/payments", tc.getRequest().getPath());
        assertNotNull(tc.getExpected());
        assertEquals(201, tc.getExpected().getStatus());
        assertEquals("Core happy path", tc.getReasoning());
    }

    @Test
    void parsesEmptyArrayToEmptyList() {
        List<TestCase> result = parser.parse("[]");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void throwsRuntimeExceptionForMalformedJson() {
        assertThrows(RuntimeException.class, () -> parser.parse("not json ["));
    }

    @Test
    void parsesAllFourTestCaseTypes() {
        String json = """
                [
                  {"id":"t1","name":"A","type":"HAPPY_PATH","priority":"P0",
                   "scenario":"s","request":{"method":"GET","path":"/x","headers":{},"body":null},
                   "expected":{"status":200,"bodyAssertions":{}},"reasoning":"r"},
                  {"id":"t2","name":"B","type":"BOUNDARY","priority":"P1",
                   "scenario":"s","request":{"method":"GET","path":"/x","headers":{},"body":null},
                   "expected":{"status":200,"bodyAssertions":{}},"reasoning":"r"},
                  {"id":"t3","name":"C","type":"NEGATIVE","priority":"P0",
                   "scenario":"s","request":{"method":"GET","path":"/x","headers":{},"body":null},
                   "expected":{"status":400,"bodyAssertions":{}},"reasoning":"r"},
                  {"id":"t4","name":"D","type":"SECURITY","priority":"P2",
                   "scenario":"s","request":{"method":"GET","path":"/x","headers":{},"body":null},
                   "expected":{"status":404,"bodyAssertions":{}},"reasoning":"r"}
                ]
                """;

        List<TestCase> cases = parser.parse(json);

        assertEquals(4, cases.size());
        assertEquals(TestCaseType.HAPPY_PATH, cases.get(0).getType());
        assertEquals(TestCaseType.BOUNDARY,   cases.get(1).getType());
        assertEquals(TestCaseType.NEGATIVE,   cases.get(2).getType());
        assertEquals(TestCaseType.SECURITY,   cases.get(3).getType());
    }
}
