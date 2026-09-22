package com.capstone.executor.consumer;

import com.capstone.executor.error.FailureClassifier;
import com.capstone.executor.error.RetryPolicy;
import com.capstone.executor.error.DeadLetterQueueHandler;
import com.capstone.executor.service.OrderExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;


@ExtendWith(MockitoExtension.class)
public class OrderPlacedConsumerErrorHandlingTest {
    
    @Mock private OrderExecutionService executionService;
    @Mock private FailureClassifier failureClassifier;
    @Mock private RetryPolicy retryPolicy;
    @Mock private DeadLetterQueueHandler dlqHandler;
    @Mock private Acknowledgment acknowledgment;
    
    private OrderPlacedConsumer consumer;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    public void setup() {
        objectMapper = new ObjectMapper();
        consumer = new OrderPlacedConsumer(executionService, objectMapper, failureClassifier, retryPolicy, dlqHandler);
        
        // Default retry policy behavior - use lenient to allow unused stubs
        lenient().when(retryPolicy.parseRetryCount(anyString())).thenReturn(0);
        // IMPORTANT: Do NOT set a global shouldRetry(anyInt()) - let tests configure their own retry limits
        lenient().when(retryPolicy.getBackoffMs(anyInt())).thenReturn(10L);  // Reduced from 100ms for faster tests
    }

    @Test
    public void testMalformedJsonDeadLetteredImmediately() {
        String malformedMessage = "{ invalid json }";
        
        // Setup classifier for malformed JSON
        lenient().when(failureClassifier.classify(any(Exception.class), anyString()))
            .thenReturn(new FailureClassifier.FailureClassification(false, "MALFORMED_JSON"));
        
        consumer.consumeOrderPlaced(malformedMessage, acknowledgment, null);
        
        // Verify DLQ was called
        verify(dlqHandler).sendToDeadLetterQueue(
            contains("invalid json"),
            contains("MALFORMED_JSON"),
            eq("orders")
        );
        
        // Verify offset was committed (moves past poison message)
        verify(acknowledgment).acknowledge();
        
        // Verify execution service was NOT called
        verify(executionService, never()).executeOrder(any());
    }
    
 
    @Test
    public void testUnexpectedEventTypeDeadLetteredImmediately() {
        String unexpectedTypeMessage = "{\"eventType\":\"UNKNOWN_EVENT\",\"payload\":{}}";
        
        consumer.consumeOrderPlaced(unexpectedTypeMessage, acknowledgment, null);
        
        // Verify DLQ was called
        verify(dlqHandler).sendToDeadLetterQueue(
            anyString(),
            contains("UNEXPECTED_EVENT_TYPE"),
            eq("orders")
        );
        
        // Verify offset was committed
        verify(acknowledgment).acknowledge();
    }
    
 
    @Test
    public void testTransientFailureIsRetried() throws Exception {
        String validMessage = "{\"eventType\":\"ORDER_PLACED\",\"payload\":{\"orderId\":\"order-1\"}}";
        
        // Setup failure - database connection issue
        doThrow(new RuntimeException("Database connection lost"))
            .when(executionService).executeOrder(any());
        
        // Setup classifier to return RECOVERABLE
        lenient().when(failureClassifier.classify(any(Exception.class), anyString()))
            .thenReturn(new FailureClassifier.FailureClassification(true, "DATABASE_UNAVAILABLE"));
        
        // Setup retry policy - allow one retry, then stop
        lenient().when(retryPolicy.parseRetryCount(null)).thenReturn(0);
        lenient().when(retryPolicy.shouldRetry(0)).thenReturn(true);   // First retry available
        lenient().when(retryPolicy.shouldRetry(1)).thenReturn(false);  // Second retry not available
        
        consumer.consumeOrderPlaced(validMessage, acknowledgment, null);
        
        // After retries exhausted, DLQ should be called with RETRY_BUDGET_EXHAUSTED
        verify(dlqHandler).sendToDeadLetterQueue(
            anyString(),
            contains("RETRY_BUDGET_EXHAUSTED"),
            eq("orders")
        );
        
        // Verify offset was committed to move past failed message
        verify(acknowledgment).acknowledge();
    }
  
    @Test
    public void testRetryBudgetExhaustedSendsToDLQ() throws Exception {
        String validMessage = "{\"eventType\":\"ORDER_PLACED\",\"payload\":{\"orderId\":\"order-2\"}}";
        
        // Setup failure
        doThrow(new RuntimeException("Database connection lost"))
            .when(executionService).executeOrder(any());
        
        // Setup classifier to return RECOVERABLE
        lenient().when(failureClassifier.classify(any(Exception.class), anyString()))
            .thenReturn(new FailureClassifier.FailureClassification(true, "DATABASE_UNAVAILABLE"));
        
        // Setup retry policy - NO MORE retries (at max)
        when(retryPolicy.parseRetryCount("3")).thenReturn(3);
        when(retryPolicy.shouldRetry(3)).thenReturn(false);
        
        consumer.consumeOrderPlaced(validMessage, acknowledgment, "3");
        
        // Verify DLQ was called with RETRY_BUDGET_EXHAUSTED reason
        verify(dlqHandler).sendToDeadLetterQueue(
            anyString(),
            contains("RETRY_BUDGET_EXHAUSTED"),
            eq("orders")
        );
        
        // Verify offset was committed (moves past failed message)
        verify(acknowledgment).acknowledge();
    }

    @Test
    public void testPoisonMessageDoesntBlockPartition() throws Exception {
        String validMessage = "{\"eventType\":\"ORDER_PLACED\",\"payload\":{\"orderId\":\"order-missing\"}}";
        
        // Setup failure - order not found (will never succeed)
        doThrow(new IllegalStateException("Order not found: order-missing"))
            .when(executionService).executeOrder(any());
        
        // Setup classifier to return UNRECOVERABLE
        when(failureClassifier.classify(any(IllegalStateException.class), anyString()))
            .thenReturn(new FailureClassifier.FailureClassification(false, "ORDER_NOT_FOUND"));
        
        consumer.consumeOrderPlaced(validMessage, acknowledgment, null);
        
        // Verify DLQ was called immediately
        verify(dlqHandler).sendToDeadLetterQueue(
            anyString(),
            contains("ORDER_NOT_FOUND"),
            eq("orders")
        );
        
        // Verify offset was committed immediately (don't wait for retry)
        verify(acknowledgment).acknowledge();
        
        // Partition is now unblocked and can process next message
    }
    

    @Test
    public void testUnknownErrorTreatedAsUnrecoverable() throws Exception {
        String validMessage = "{\"eventType\":\"ORDER_PLACED\",\"payload\":{\"orderId\":\"order-3\"}}";
        
        // Setup failure - unexpected runtime error
        doThrow(new RuntimeException("Unexpected error"))
            .when(executionService).executeOrder(any());
        
        // Setup classifier to return UNRECOVERABLE (fail-safe default)
        when(failureClassifier.classify(any(RuntimeException.class), anyString()))
            .thenReturn(new FailureClassifier.FailureClassification(false, "UNKNOWN_ERROR"));
        
        consumer.consumeOrderPlaced(validMessage, acknowledgment, null);
        
        // Verify DLQ was called (fail-safe: treat as unrecoverable)
        verify(dlqHandler).sendToDeadLetterQueue(
            anyString(),
            contains("UNKNOWN_ERROR"),
            eq("orders")
        );
        
        // Verify offset was committed
        verify(acknowledgment).acknowledge();
    }
}
