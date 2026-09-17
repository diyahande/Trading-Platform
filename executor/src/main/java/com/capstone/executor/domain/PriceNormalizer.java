package com.capstone.executor.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Price normalization to database precision: NUMERIC(20,4) with ROUND_HALF_UP
public class PriceNormalizer {
    private static final int SCALE = 4;
    private static final RoundingMode MODE = RoundingMode.HALF_UP;
    
    public static BigDecimal normalize(BigDecimal price) {
        if (price == null) return null;
        return price.setScale(SCALE, MODE);
    }
}
