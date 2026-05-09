package com.testforge.ai.cache;

import com.testforge.ai.model.Priority;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.model.TestCaseExpected;
import com.testforge.ai.model.TestCaseRequest;
import com.testforge.ai.model.TestCaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileBasedEndpointCacheTest {

    @TempDir
    Path tempDir;

    private FileBasedEndpointCache cache() {
        return new FileBasedEndpointCache(tempDir);
    }

    private TestCase sampleTestCase() {
        TestCase tc = new TestCase();
        tc.setId("tc-001");
        tc.setName("Happy path");
        tc.setType(TestCaseType.HAPPY_PATH);
        tc.setPriority(Priority.P0);
        tc.setScenario("Should return 200");
        TestCaseRequest req = new TestCaseRequest();
        req.setMethod("POST");
        req.setPath("/accounts");
        tc.setRequest(req);
        TestCaseExpected exp = new TestCaseExpected();
        exp.setStatus(200);
        tc.setExpected(exp);
        return tc;
    }

    @Test
    void saveAndFindReturnsStoredTestCases() {
        FileBasedEndpointCache c = cache();
        List<TestCase> cases = List.of(sampleTestCase());

        c.save("abc123", cases);
        Optional<List<TestCase>> found = c.findByFingerprint("abc123");

        assertTrue(found.isPresent());
        assertEquals(1, found.get().size());
        assertEquals("tc-001", found.get().get(0).getId());
    }

    @Test
    void findForMissingFingerprintReturnsEmpty() {
        Optional<List<TestCase>> found = cache().findByFingerprint("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void saveCreatesDirectoryIfAbsent() {
        Path nestedDir = tempDir.resolve("nested/cache");
        FileBasedEndpointCache c = new FileBasedEndpointCache(nestedDir);

        c.save("fp1", List.of(sampleTestCase()));

        Optional<List<TestCase>> found = c.findByFingerprint("fp1");
        assertTrue(found.isPresent());
    }
}
