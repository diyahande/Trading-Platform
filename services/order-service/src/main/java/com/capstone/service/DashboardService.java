package com.capstone.service;

import com.capstone.dto.DashboardResponse;
import com.capstone.dto.PortfolioAllocationResponse;
import com.capstone.dto.PositionResponse;
import com.capstone.entity.Account;
import com.capstone.entity.Instrument;
import com.capstone.exception.InstrumentNotFoundException;
import com.capstone.exception.MarketDataUnavailableException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

    private final AccountService accountService;
    private final InstrumentService instrumentService;
    private final FxConversionService fxConversionService;
    private final KafkaMarketQuoteService kafkaMarketQuoteService;

    public DashboardResponse getDashboardSummaryForAuthorizedAccount(String accountReference) {
        Account account = accountService.resolveAccountFromToken(accountReference);
        List<PositionResponse> positions = accountService.getPositionsByAccountId(account.getAccountId());

        BigDecimal availableCash = scaleMoney(account.getCashBalance());
        BigDecimal holdingsMarketValue = BigDecimal.ZERO;
        BigDecimal todaysPnl = BigDecimal.ZERO;
        boolean canComputePortfolioValue = true;
        boolean canComputeTodaysPnl = true;

        for (PositionResponse position : positions) {
            if (position.getQuantity() == null || position.getQuantity() <= 0) {
                continue;
            }

            Instrument instrument = instrumentService.getInstrumentBySymbol(position.getSymbol())
                    .orElseThrow(() -> new InstrumentNotFoundException(position.getSymbol()));

            KafkaMarketQuoteService.QuoteSnapshot quote;
            try {
                quote = kafkaMarketQuoteService.getQuote(position.getSymbol());
            } catch (MarketDataUnavailableException ex) {
                canComputePortfolioValue = false;
                canComputeTodaysPnl = false;
                break;
            }

            logger.debug(
                    "Dashboard valuation using cached quote: symbol={} source={} receivedAt={} price={} previousClose={}",
                    quote.symbol(),
                    quote.source(),
                    quote.receivedAt(),
                    quote.price(),
                    quote.previousClose()
            );

            BigDecimal quantity = BigDecimal.valueOf(position.getQuantity());
            BigDecimal positionCurrentValue = convertToAccountCurrency(
                    quote.price().multiply(quantity),
                    instrument.getQuoteCurrency(),
                    account.getCurrency()
            );
            holdingsMarketValue = holdingsMarketValue.add(positionCurrentValue);

            if (quote.previousClose() == null) {
                canComputeTodaysPnl = false;
                continue;
            }

            BigDecimal positionPreviousCloseValue = convertToAccountCurrency(
                    quote.previousClose().multiply(quantity),
                    instrument.getQuoteCurrency(),
                    account.getCurrency()
            );
            todaysPnl = todaysPnl.add(positionCurrentValue.subtract(positionPreviousCloseValue));
        }

            BigDecimal portfolioValue = canComputePortfolioValue ? availableCash.add(holdingsMarketValue) : null;
            BigDecimal pnlValue = canComputeTodaysPnl ? todaysPnl : null;

        // BUY orders reserve cash at placement today, so available cash already reflects buying power.
        BigDecimal buyingPower = availableCash;

        return new DashboardResponse(
                portfolioValue == null ? null : scaleMoney(portfolioValue),
                pnlValue == null ? null : scaleMoney(pnlValue),
                availableCash,
                scaleMoney(buyingPower)
        );
    }

    public PortfolioAllocationResponse getPortfolioAllocationForAuthorizedAccount(String accountReference) {
        Account account = accountService.resolveAccountFromToken(accountReference);
        List<PositionResponse> positions = accountService.getPositionsByAccountId(account.getAccountId());

        BigDecimal availableCash = scaleMoney(account.getCashBalance());
        BigDecimal totalPortfolioValue = availableCash;
        Map<String, BigDecimal> allocationByAssetClass = new HashMap<>();

        for (PositionResponse position : positions) {
            if (position.getQuantity() == null || position.getQuantity() <= 0) {
                continue;
            }

            Instrument instrument = instrumentService.getInstrumentBySymbol(position.getSymbol())
                    .orElseThrow(() -> new InstrumentNotFoundException(position.getSymbol()));

            try {
                KafkaMarketQuoteService.QuoteSnapshot quote = kafkaMarketQuoteService.getQuote(position.getSymbol());

                logger.debug(
                    "Portfolio allocation using cached quote: symbol={} source={} receivedAt={} price={}",
                    quote.symbol(),
                    quote.source(),
                    quote.receivedAt(),
                    quote.price()
                );
                
                BigDecimal quantity = BigDecimal.valueOf(position.getQuantity());
                BigDecimal positionCurrentValue = convertToAccountCurrency(
                        quote.price().multiply(quantity),
                        instrument.getQuoteCurrency(),
                        account.getCurrency()
                );

                totalPortfolioValue = totalPortfolioValue.add(positionCurrentValue);

                String assetClass = instrument.getAssetClass().name();
                allocationByAssetClass.put(
                        assetClass,
                        allocationByAssetClass.getOrDefault(assetClass, BigDecimal.ZERO).add(positionCurrentValue)
                );
            } catch (MarketDataUnavailableException ex) {
                // Skip positions where market data is unavailable
                continue;
            }
        }

        // Calculate percentages
        Map<String, BigDecimal> allocationPercentages = new HashMap<>();
        if (totalPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
            for (Map.Entry<String, BigDecimal> entry : allocationByAssetClass.entrySet()) {
                BigDecimal percentage = entry.getValue()
                        .divide(totalPortfolioValue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
                allocationPercentages.put(entry.getKey(), percentage);
            }

            // Add cash allocation
            BigDecimal cashPercentage = availableCash
                    .divide(totalPortfolioValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            allocationPercentages.put("CASH", cashPercentage);
        }

        return new PortfolioAllocationResponse(
                scaleMoney(totalPortfolioValue),
                scaleMoney(availableCash),
                allocationPercentages
        );
    }

    private BigDecimal convertToAccountCurrency(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }

        return fxConversionService.convert(amount, fromCurrency, toCurrency);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}