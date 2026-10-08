package com.capstone.executor.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

// Unit tests for pure FillRule: no DB, no socket dependency
public class FillRuleTest {
    
    // Test 1: BUY limit >= ask fills at ask
    @Test
    public void testBuyAtOrAboveAskFills() {
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        
        // Exactly at ask
        ExecutionDecision decision = FillRule.decide("BUY", new BigDecimal("232.60"), quote);
        assertTrue(decision.isFill());
        assertEquals(new BigDecimal("232.60"), decision.getExecutionPrice());
        
        // Above ask
        decision = FillRule.decide("BUY", new BigDecimal("233.00"), quote);
        assertTrue(decision.isFill());
        assertEquals(new BigDecimal("232.60"), decision.getExecutionPrice());
    }
    
    // Test 2: SELL limit <= bid fills at bid
    @Test
    public void testSellAtOrBelowBidFills() {
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        
        // Exactly at bid
        ExecutionDecision decision = FillRule.decide("SELL", new BigDecimal("232.40"), quote);
        assertTrue(decision.isFill());
        assertEquals(new BigDecimal("232.40"), decision.getExecutionPrice());
        
        // Below bid
        decision = FillRule.decide("SELL", new BigDecimal("232.00"), quote);
        assertTrue(decision.isFill());
        assertEquals(new BigDecimal("232.40"), decision.getExecutionPrice());
    }
    
    // Test 3: Order outside marketable range rejected
    @Test
    public void testOrderOutsideMarketableRangeRejected() {
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        
        // BUY below ask
        ExecutionDecision decision = FillRule.decide("BUY", new BigDecimal("232.50"), quote);
        assertTrue(decision.isReject());
        assertEquals("PRICE_NOT_MET", decision.getRejectReason());
        
        // SELL above bid
        decision = FillRule.decide("SELL", new BigDecimal("232.50"), quote);
        assertTrue(decision.isReject());
        assertEquals("PRICE_NOT_MET", decision.getRejectReason());
    }
    
    // Test 4: No price available rejected
    @Test
    public void testNoPriceAvailableRejected() {
        // Null quote
        ExecutionDecision decision = FillRule.decide("BUY", new BigDecimal("230.00"), null);
        assertTrue(decision.isReject());
        assertEquals("PRICING_UNAVAILABLE", decision.getRejectReason());
        
        // Quote with null price
        Quote quoteNullPrice = new Quote("AAPL", null, new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        decision = FillRule.decide("BUY", new BigDecimal("230.00"), quoteNullPrice);
        assertTrue(decision.isReject());
        assertEquals("PRICING_UNAVAILABLE", decision.getRejectReason());
        
        // Null bid/ask
        Quote quoteNullSides = new Quote("AAPL", new BigDecimal("232.50"), null, null, "USD", null, null, null, "open", false, Instant.now());
        decision = FillRule.decide("BUY", new BigDecimal("230.00"), quoteNullSides);
        assertTrue(decision.isReject());
    }
    
    // Test 5: Price precision test - exact fills
    @Test
    public void testExactPrices() {
        Quote quote = new Quote("MSFT", new BigDecimal("380.00"), new BigDecimal("379.90"), new BigDecimal("380.10"), "USD", null, null, null, "open", false, Instant.now());
        
        // BUY exact ask
        ExecutionDecision decision = FillRule.decide("BUY", new BigDecimal("380.10"), quote);
        assertTrue(decision.isFill());
        assertEquals(new BigDecimal("380.10"), decision.getExecutionPrice());
        
        // SELL exact bid
        decision = FillRule.decide("SELL", new BigDecimal("379.90"), quote);
        assertTrue(decision.isFill());
        assertEquals(new BigDecimal("379.90"), decision.getExecutionPrice());
    }
    
    // Test 6: Invalid side
    @Test
    public void testInvalidSideRejected() {
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        ExecutionDecision decision = FillRule.decide("INVALID", new BigDecimal("230.00"), quote);
        assertTrue(decision.isReject());
        assertEquals("INVALID_SIDE", decision.getRejectReason());
    }
}
