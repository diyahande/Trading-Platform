package com.capstone.service;

import com.capstone.dto.DashboardResponse;
import com.capstone.dto.PositionResponse;
import com.capstone.entity.Account;
import com.capstone.entity.Instrument;
import com.capstone.exception.InstrumentNotFoundException;
import com.capstone.exception.MarketDataUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AccountService accountService;
    private final InstrumentService instrumentService;
    private final FxConversionService fxConversionService;
    private final MarketQuoteService marketQuoteService;

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

            MarketQuoteService.QuoteSnapshot quote;
            try {
                quote = marketQuoteService.getQuote(position.getSymbol());
            } catch (MarketDataUnavailableException ex) {
                canComputePortfolioValue = false;
                canComputeTodaysPnl = false;
                break;
            }

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