package com.capstone.service;

import com.capstone.dto.DashboardResponse;
import com.capstone.dto.PositionResponse;
import com.capstone.entity.Account;
import com.capstone.entity.Instrument;
import com.capstone.enums.AccountStatus;
import com.capstone.enums.AssetClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private AccountService accountService;
    @Mock
    private InstrumentService instrumentService;
    @Mock
    private FxConversionService fxConversionService;
    @Mock
    private MarketQuoteService marketQuoteService;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(accountService, instrumentService, fxConversionService, marketQuoteService);
    }

    @Test
    void getDashboardSummary_calculatesPortfolioValueAvailableCashBuyingPowerAndTodaysPnl() {
        Account account = account(new BigDecimal("25000.00"));
        when(accountService.resolveAccountFromToken("acc-ref")).thenReturn(account);
        when(accountService.getPositionsByAccountId(1L)).thenReturn(List.of(
                new PositionResponse("acc-ref", "AAPL", 10, new BigDecimal("170.00")),
                new PositionResponse("acc-ref", "NVDA", 15, new BigDecimal("900.00"))
        ));

        when(instrumentService.getInstrumentBySymbol("AAPL")).thenReturn(Optional.of(instrument("AAPL", "USD")));
        when(instrumentService.getInstrumentBySymbol("NVDA")).thenReturn(Optional.of(instrument("NVDA", "USD")));

        when(marketQuoteService.getQuote("AAPL")).thenReturn(
                new MarketQuoteService.QuoteSnapshot("AAPL", new BigDecimal("182.00"), new BigDecimal("176.00"), "USD")
        );
        when(marketQuoteService.getQuote("NVDA")).thenReturn(
                new MarketQuoteService.QuoteSnapshot("NVDA", new BigDecimal("946.00"), new BigDecimal("940.00"), "USD")
        );

        DashboardResponse result = dashboardService.getDashboardSummaryForAuthorizedAccount("acc-ref");

        assertEquals(new BigDecimal("41010.00"), result.getPortfolioValue());
        assertEquals(new BigDecimal("150.00"), result.getTodaysPnl());
        assertEquals(new BigDecimal("25000.00"), result.getAvailableCash());
        assertEquals(new BigDecimal("25000.00"), result.getBuyingPower());
    }

    @Test
    void getDashboardSummary_noHoldings_returnsCashOnlyAndZeroPnl() {
        Account account = account(new BigDecimal("25000.00"));
        when(accountService.resolveAccountFromToken("acc-ref")).thenReturn(account);
        when(accountService.getPositionsByAccountId(1L)).thenReturn(List.of());

        DashboardResponse result = dashboardService.getDashboardSummaryForAuthorizedAccount("acc-ref");

        assertEquals(new BigDecimal("25000.00"), result.getPortfolioValue());
        assertEquals(new BigDecimal("0.00"), result.getTodaysPnl());
        assertEquals(new BigDecimal("25000.00"), result.getAvailableCash());
        assertEquals(new BigDecimal("25000.00"), result.getBuyingPower());
    }

    @Test
    void getDashboardSummary_missingPreviousClose_returnsNullPnlButKeepsOtherValues() {
        Account account = account(new BigDecimal("1000.00"));
        when(accountService.resolveAccountFromToken("acc-ref")).thenReturn(account);
        when(accountService.getPositionsByAccountId(1L)).thenReturn(List.of(
                new PositionResponse("acc-ref", "AAPL", 10, new BigDecimal("170.00"))
        ));

        when(instrumentService.getInstrumentBySymbol("AAPL")).thenReturn(Optional.of(instrument("AAPL", "USD")));
        when(marketQuoteService.getQuote("AAPL")).thenReturn(
                new MarketQuoteService.QuoteSnapshot("AAPL", new BigDecimal("182.00"), null, "USD")
        );

        DashboardResponse result = dashboardService.getDashboardSummaryForAuthorizedAccount("acc-ref");

        assertEquals(new BigDecimal("2820.00"), result.getPortfolioValue());
        assertEquals(null, result.getTodaysPnl());
        assertEquals(new BigDecimal("1000.00"), result.getAvailableCash());
        assertEquals(new BigDecimal("1000.00"), result.getBuyingPower());
    }

    private static Account account(BigDecimal balance) {
        return new Account(
                1L,
                9L,
                "acc-ref",
                "Trader",
                "USD",
                balance,
                AccountStatus.ACTIVE,
                1L,
                Instant.parse("2024-01-01T00:00:00Z")
        );
    }

    private static Instrument instrument(String symbol, String quoteCurrency) {
        return new Instrument(
                11L,
                symbol,
                symbol,
                AssetClass.EQUITY,
                quoteCurrency,
                false,
                Instant.parse("2024-01-01T00:00:00Z")
        );
    }
}