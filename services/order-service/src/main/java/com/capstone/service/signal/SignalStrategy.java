package com.capstone.service.signal;

import com.capstone.dto.SignalIndicatorsResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

@Component
public class SignalStrategy {

    public StrategyResult evaluate(SignalIndicatorsResponse indicators) {
        int buyScore = 0;
        int sellScore = 0;

        List<String> reasons = new ArrayList<>();
        List<String> buyContributors = new ArrayList<>();
        List<String> sellContributors = new ArrayList<>();

        int trendCmp = indicators.getSma20().compareTo(indicators.getSma50());
        if (trendCmp > 0) {
            buyScore += 30;
            String reason = "SMA20 is above SMA50 (BUY +30)";
            reasons.add(reason);
            buyContributors.add("SMA20 is above SMA50");
        } else if (trendCmp < 0) {
            sellScore += 30;
            String reason = "SMA20 is below SMA50 (SELL +30)";
            reasons.add(reason);
            sellContributors.add("SMA20 is below SMA50");
        } else {
            reasons.add("SMA20 equals SMA50 (no trend score)");
        }

        int rsiCmpLow = indicators.getRsi14().compareTo(java.math.BigDecimal.valueOf(30));
        int rsiCmpHigh = indicators.getRsi14().compareTo(java.math.BigDecimal.valueOf(70));
        if (rsiCmpLow < 0) {
            buyScore += 25;
            String reason = "RSI(14) is below 30 (BUY +25)";
            reasons.add(reason);
            buyContributors.add("RSI is oversold");
        } else if (rsiCmpHigh > 0) {
            sellScore += 25;
            String reason = "RSI(14) is above 70 (SELL +25)";
            reasons.add(reason);
            sellContributors.add("RSI is overbought");
        } else {
            reasons.add("RSI(14) is neutral between 30 and 70 (no RSI score)");
        }

        int momentumCmp = indicators.getMomentum10d().compareTo(java.math.BigDecimal.ZERO);
        if (momentumCmp > 0) {
            buyScore += 20;
            String reason = "10-day momentum is positive (BUY +20)";
            reasons.add(reason);
            buyContributors.add("10-day momentum is positive");
        } else if (momentumCmp < 0) {
            sellScore += 20;
            String reason = "10-day momentum is negative (SELL +20)";
            reasons.add(reason);
            sellContributors.add("10-day momentum is negative");
        } else {
            reasons.add("10-day momentum is flat (no momentum score)");
        }

        int priceVsSmaCmp = indicators.getCurrentPrice().compareTo(indicators.getSma20());
        if (priceVsSmaCmp > 0) {
            buyScore += 15;
            String reason = "Current price is above SMA20 (BUY +15)";
            reasons.add(reason);
            buyContributors.add("Current price is above SMA20");
        } else if (priceVsSmaCmp < 0) {
            sellScore += 15;
            String reason = "Current price is below SMA20 (SELL +15)";
            reasons.add(reason);
            sellContributors.add("Current price is below SMA20");
        } else {
            reasons.add("Current price equals SMA20 (no price-vs-SMA score)");
        }

        String signal;
        if (buyScore >= 60 && buyScore > sellScore) {
            signal = "BUY";
        } else if (sellScore >= 60 && sellScore > buyScore) {
            signal = "SELL";
        } else {
            signal = "HOLD";
        }

        int strength = Math.max(buyScore, sellScore);
        String rationale = buildRationale(signal, buyScore, sellScore, buyContributors, sellContributors);

        return new StrategyResult(signal, strength, buyScore, sellScore, reasons, rationale);
    }

    private String buildRationale(
            String signal,
            int buyScore,
            int sellScore,
            List<String> buyContributors,
            List<String> sellContributors) {
        if ("BUY".equals(signal)) {
            return "BUY - " + joinPhrases(buyContributors);
        }
        if ("SELL".equals(signal)) {
            return "SELL - " + joinPhrases(sellContributors);
        }
        return String.format("HOLD - no dominant rule-based conviction (buyScore=%d, sellScore=%d)", buyScore, sellScore);
    }

    private String joinPhrases(List<String> phrases) {
        if (phrases.isEmpty()) {
            return "no contributing conditions.";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (String phrase : phrases) {
            joiner.add(phrase);
        }
        return joiner + ".";
    }

    public record StrategyResult(
            String signal,
            Integer strength,
            Integer buyScore,
            Integer sellScore,
            List<String> reasons,
            String rationale) {
    }
}
