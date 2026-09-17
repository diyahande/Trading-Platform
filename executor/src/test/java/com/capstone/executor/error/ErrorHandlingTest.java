package com.capstone.executor.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for error classification and retry logic
 */
@ExtendWith(MockitoExtension.class)
public class ErrorHandlingTest {
    
    private final FailureClassifier classifier = new FailureClassifier();
    private final RetryPolicy retryPolicy = new RetryPolicy();
    
    // ===== ERROR CLASSIFICATION TESTS =====
    
    /**
     * Test 1: Malformed JSON is classified as UNRECOVERABLE
     */
    @Test
    public void testMalformedJsonIsUnrecoverable() {
        JsonProcessingException ex = mock(JsonProcessingException.class);
        when(ex.getMessage()).thenReturn("Invalid JSON");
        FailureClassifier.FailureClassification result = classifier.classify(ex, "invalid json");
        
        assertFalse(result.isRecoverable(), "Malformed JSON should be unrecoverable");
        assertTrue(result.getReason().contains("MALFORMED_JSON"), "Reason should mention malformed JSON");
    }
    
    /**
     * Test 2: Missing orderId is classified as UNRECOVERABLE
     */
    @Test
    public void testMissingOrderIdIsUnrecoverable() {
        IllegalArgumentException ex = new IllegalArgumentException("orderId is required");
        FailureClassifier.FailureClassification result = classifier.classify(ex, "{}");
        
        assertFalse(result.isRecoverable(), "Missing orderId should be unrecoverable");
        assertTrue(result.getReason().contains("MISSING_ORDER_ID"), "Reason should mention missing orderId");
    }
    
    /**
     * Test 3: Unexpected event type is classified as UNRECOVERABLE
     */
    @Test
    public void testUnexpectedEventTypeIsUnrecoverable() {
        IllegalArgumentException ex = new IllegalArgumentException("eventType must be ORDER_PLACED");
        FailureClassifier.FailureClassification result = classifier.classify(ex, "{}");
        
        assertFalse(result.isRecoverable(), "Unexpected event type should be unrecoverable");
        assertTrue(result.getReason().contains("UNEXPECTED_EVENT_TYPE"), "Reason should mention unexpected event type");
    }
    
    /**
     * Test 4: Order not found is classified as UNRECOVERABLE
     */
    @Test
    public void testOrderNotFoundIsUnrecoverable() {
        IllegalStateException ex = new IllegalStateException("Order not found: order-123");
        FailureClassifier.FailureClassification result = classifier.classify(ex, "{}");
        
        assertFalse(result.isRecoverable(), "Order not found should be unrecoverable");
        assertTrue(result.getReason().contains("ORDER_NOT_FOUND"), "Reason should mention order not found");
    }
    
    /**
     * Test 5: Database connection issue is classified as RECOVERABLE
     */
    @Test
    public void testDatabaseConnectionIsRecoverable() {
        SQLException sqlEx = new SQLException("Connection refused");
        Exception wrappedException = new RuntimeException(sqlEx);
        FailureClassifier.FailureClassification result = classifier.classify(wrappedException, "{}");
        
        assertTrue(result.isRecoverable(), "Database connection issue should be recoverable");
        assertTrue(result.getReason().contains("DATABASE_UNAVAILABLE"), "Reason should mention database unavailable");
    }
    
    /**
     * Test 6: Broker unreachable (network failure) is classified as RECOVERABLE
     */
    @Test
    public void testBrokerUnreachableIsRecoverable() {
        Exception ex = new java.net.ConnectException("Connection refused");
        FailureClassifier.FailureClassification result = classifier.classify(ex, "{}");
        
        assertTrue(result.isRecoverable(), "Broker unreachable should be recoverable");
        assertTrue(result.getReason().contains("NETWORK_UNAVAILABLE"), "Reason should mention network unavailable");
    }
    
    /**
     * Test 7: Lock conflict (optimistic lock) is classified as RECOVERABLE
     */
    @Test
    public void testLockConflictIsRecoverable() {
        RuntimeException ex = new RuntimeException("Optimistic lock failed");
        FailureClassifier.FailureClassification result = classifier.classify(ex, "{}");
        
        assertTrue(result.isRecoverable(), "Lock conflict should be recoverable");
        assertTrue(result.getReason().contains("LOCK_CONFLICT"), "Reason should mention lock conflict");
    }
    
    // ===== RETRY POLICY TESTS =====
    
    /**
     * Test 8: Retry allowed when under max attempts
     */
    @Test
    public void testRetryAllowedUnderMaxAttempts() {
        assertTrue(retryPolicy.shouldRetry(0), "Should allow retry at attempt 0");
        assertTrue(retryPolicy.shouldRetry(1), "Should allow retry at attempt 1");
        assertTrue(retryPolicy.shouldRetry(2), "Should allow retry at attempt 2");
    }
    
    /**
     * Test 9: Retry denied when budget exhausted
     */
    @Test
    public void testRetryDeniedWhenBudgetExhausted() {
        assertFalse(retryPolicy.shouldRetry(3), "Should deny retry when max retries reached");
        assertFalse(retryPolicy.shouldRetry(4), "Should deny retry when max retries exceeded");
    }
    
    /**
     * Test 10: Backoff increases exponentially
     */
    @Test
    public void testBackoffIncreasesExponentially() {
        long backoff0 = retryPolicy.getBackoffMs(0);
        long backoff1 = retryPolicy.getBackoffMs(1);
        long backoff2 = retryPolicy.getBackoffMs(2);
        
        assertTrue(backoff0 < backoff1, "Backoff should increase from attempt 0 to 1");
        assertTrue(backoff1 < backoff2, "Backoff should increase from attempt 1 to 2");
        assertTrue(backoff2 >= backoff1 * 2, "Backoff should roughly double with each attempt");
    }
    
    /**
     * Test 11: Backoff respects maximum
     */
    @Test
    public void testBackoffRespectsMaximum() {
        long backoff10 = retryPolicy.getBackoffMs(10);
        assertTrue(backoff10 <= 5000, "Backoff should not exceed 5000ms");
    }
    
    /**
     * Test 12: Parse retry count from header
     */
    @Test
    public void testParseRetryCountFromHeader() {
        assertEquals(0, retryPolicy.parseRetryCount(null), "Null header should parse as 0");
        assertEquals(0, retryPolicy.parseRetryCount("0"), "Header '0' should parse as 0");
        assertEquals(2, retryPolicy.parseRetryCount("2"), "Header '2' should parse as 2");
        assertEquals(0, retryPolicy.parseRetryCount("invalid"), "Invalid header should parse as 0");
    }
}
