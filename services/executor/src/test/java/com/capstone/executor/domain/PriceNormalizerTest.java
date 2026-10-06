package com.capstone.executor.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

// Unit tests for price normalization
public class PriceNormalizerTest {
    
    // Test price with more decimals than database supports
    @Test
    public void testRoundHalfUp() {
        // Test ROUND_HALF_UP: 149.87654321 rounds to 149.8765 at scale 4
        BigDecimal price = new BigDecimal("149.87654321");
        BigDecimal normalized = PriceNormalizer.normalize(price);
        assertEquals(new BigDecimal("149.8765"), normalized);
        assertEquals(4, normalized.scale());
    }
    
    // Test null price
    @Test
    public void testNullPrice() {
        assertNull(PriceNormalizer.normalize(null));
    }
    
    // Test price already at correct scale
    @Test
    public void testAlreadyCorrectScale() {
        BigDecimal price = new BigDecimal("232.60");
        BigDecimal normalized = PriceNormalizer.normalize(price);
        assertEquals(new BigDecimal("232.6000"), normalized);
        assertEquals(4, normalized.scale());
    }
    
    // Test rounding up
    @Test
    public void testRoundingUp() {
        BigDecimal price = new BigDecimal("100.123456");
        BigDecimal normalized = PriceNormalizer.normalize(price);
        assertEquals(new BigDecimal("100.1235"), normalized);
    }
    
    // Test rounding down
    @Test
    public void testRoundingDown() {
        BigDecimal price = new BigDecimal("100.123412");
        BigDecimal normalized = PriceNormalizer.normalize(price);
        assertEquals(new BigDecimal("100.1234"), normalized);
    }
}
