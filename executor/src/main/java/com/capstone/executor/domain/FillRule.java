package com.capstone.executor.domain;

import java.math.BigDecimal;

public class FillRule {

    private FillRule() {
    }

    public static ExecutionDecision decide(
            String side,
            BigDecimal limitPrice,
            Quote quote) {

        if (quote == null) {
            return ExecutionDecision.reject("PRICING_UNAVAILABLE");
        }

        if (limitPrice == null) {
            return ExecutionDecision.reject("PRICING_UNAVAILABLE");
        }

        // Check that quote has valid mid price
        if (quote.getPrice() == null) {
            return ExecutionDecision.reject("PRICING_UNAVAILABLE");
        }

        if ("BUY".equalsIgnoreCase(side)) {

            if (quote.getAsk() == null) {
                return ExecutionDecision.reject("PRICING_UNAVAILABLE");
            }

            if (limitPrice.compareTo(quote.getAsk()) >= 0) {
                return ExecutionDecision.fill(quote.getAsk());
            }

            return ExecutionDecision.reject("PRICE_NOT_MET");
        }

        if ("SELL".equalsIgnoreCase(side)) {

            if (quote.getBid() == null) {
                return ExecutionDecision.reject("PRICING_UNAVAILABLE");
            }

            if (limitPrice.compareTo(quote.getBid()) <= 0) {
                return ExecutionDecision.fill(quote.getBid());
            }

            return ExecutionDecision.reject("PRICE_NOT_MET");
        }

        return ExecutionDecision.reject("INVALID_SIDE");
    }
}