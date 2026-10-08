package com.capstone.service.signal;

import com.capstone.dto.CandleDataResponse;
import com.capstone.dto.SignalIndicatorsResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class TechnicalIndicatorCalculator {

    public static final int MIN_REQUIRED_CANDLES = 50;

    public SignalIndicatorsResponse calculate(List<CandleDataResponse> candles) {
        List<BigDecimal> closes = extractValidCloses(candles);
        if (closes.size() < MIN_REQUIRED_CANDLES) {
            throw new IllegalArgumentException("Insufficient valid candles: require at least 50 daily closes");
        }

        BigDecimal latestClose = closes.get(closes.size() - 1);
        BigDecimal close10DaysAgo = closes.get(closes.size() - 11);

        BigDecimal sma20 = average(closes.subList(closes.size() - 20, closes.size()));
        BigDecimal sma50 = average(closes.subList(closes.size() - 50, closes.size()));
        BigDecimal rsi14 = computeRsi14(closes);
        BigDecimal momentum10d = latestClose
                .subtract(close10DaysAgo)
                .divide(close10DaysAgo, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        SignalIndicatorsResponse indicators = new SignalIndicatorsResponse();
        indicators.setCurrentPrice(scale(latestClose, 4));
        indicators.setClose10DaysAgo(scale(close10DaysAgo, 4));
        indicators.setSma20(scale(sma20, 4));
        indicators.setSma50(scale(sma50, 4));
        indicators.setRsi14(scale(rsi14, 2));
        indicators.setMomentum10d(scale(momentum10d, 2));
        return indicators;
    }

    private List<BigDecimal> extractValidCloses(List<CandleDataResponse> candles) {
        if (candles == null) {
            return List.of();
        }

        List<CandleDataResponse> ordered = candles.stream()
                .filter(candle -> candle != null && candle.getDate() != null)
                .sorted(Comparator.comparing(CandleDataResponse::getDate))
                .toList();

        List<BigDecimal> closes = new ArrayList<>(ordered.size());
        for (CandleDataResponse candle : ordered) {
            BigDecimal close = candle.getClose();
            if (close != null && close.compareTo(BigDecimal.ZERO) > 0) {
                closes.add(close);
            }
        }
        return closes;
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal computeRsi14(List<BigDecimal> closes) {
        if (closes.size() < 15) {
            throw new IllegalArgumentException("Insufficient closes for RSI(14)");
        }

        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;
        for (int i = 1; i <= 14; i++) {
            BigDecimal delta = closes.get(i).subtract(closes.get(i - 1));
            if (delta.signum() > 0) {
                avgGain = avgGain.add(delta);
            } else if (delta.signum() < 0) {
                avgLoss = avgLoss.add(delta.abs());
            }
        }

        avgGain = avgGain.divide(BigDecimal.valueOf(14), 8, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(BigDecimal.valueOf(14), 8, RoundingMode.HALF_UP);

        for (int i = 15; i < closes.size(); i++) {
            BigDecimal delta = closes.get(i).subtract(closes.get(i - 1));
            BigDecimal gain = delta.signum() > 0 ? delta : BigDecimal.ZERO;
            BigDecimal loss = delta.signum() < 0 ? delta.abs() : BigDecimal.ZERO;

            avgGain = avgGain.multiply(BigDecimal.valueOf(13))
                    .add(gain)
                    .divide(BigDecimal.valueOf(14), 8, RoundingMode.HALF_UP);
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(13))
                    .add(loss)
                    .divide(BigDecimal.valueOf(14), 8, RoundingMode.HALF_UP);
        }

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return avgGain.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.valueOf(50)
                    : BigDecimal.valueOf(100);
        }

        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100)
                        .divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}
