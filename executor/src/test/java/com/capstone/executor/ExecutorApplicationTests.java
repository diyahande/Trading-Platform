package com.capstone.executor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Smoke test - verify application main class is accessible
class ExecutorApplicationTests {

    @Test
    void applicationClassExists() {
        assertNotNull(ExecutorApplication.class);
    }

}
