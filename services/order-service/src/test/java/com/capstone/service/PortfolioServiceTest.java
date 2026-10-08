package com.capstone.service;

import com.capstone.dto.FilledOrderRow;
import com.capstone.dto.PortfolioHoldingRow;
import com.capstone.entity.Account;
import com.capstone.enums.AccountStatus;
import com.capstone.enums.OrderSide;
import com.capstone.exception.InvalidInputException;
import com.capstone.mapper.PortfolioMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private AccountService accountService;
    @Mock
    private PortfolioMapper portfolioMapper;
    @Mock
    private FauxnanceBatchQuoteService fauxnanceBatchQuoteService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        portfolioService = new PortfolioService(accountService, portfolioMapper, fauxnanceBatchQuoteService, jdbcTemplate);
    }

    @Test
    void getPnl_throwsInvalidInputWhenFromAfterTo() {
        assertThrows(InvalidInputException.class,
                () -> portfolioService.getPnl("ref", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1), false));
    }

    @Test
    void getPortfolioSummary_computesTotalsFromPricedPositions() {
        Account account = new Account(1L, 1L, "ref", "Trader", "USD", new BigDecimal("100.00"), AccountStatus.ACTIVE, 1L, Instant.now());
        when(accountService.getAccountByReferenceStrict("ref")).thenReturn(account);

        when(portfolioMapper.findOpenHoldingsByAccountId(1L)).thenReturn(List.of(
                new PortfolioHoldingRow(1L, "AAPL", 2, new BigDecimal("10.00"), "USD")
        ));
        when(portfolioMapper.findFilledOrdersForAccount(1L)).thenReturn(List.of(
                new FilledOrderRow("AAPL", OrderSide.BUY, 2, new BigDecimal("10.00"), OffsetDateTime.parse("2026-01-01T00:00:00Z")),
                new FilledOrderRow("AAPL", OrderSide.SELL, 1, new BigDecimal("12.00"), OffsetDateTime.parse("2026-02-01T00:00:00Z"))
        ));

        when(fauxnanceBatchQuoteService.getQuotes(List.of("AAPL"))).thenReturn(Map.of(
                "AAPL", new FauxnanceBatchQuoteService.QuoteResult(new BigDecimal("11.00"), "USD", OffsetDateTime.parse("2026-10-10T00:00:00Z"), false)
        ));

        var summary = portfolioService.getPortfolioSummary("ref");

        assertEquals(new BigDecimal("22.00"), summary.getMarketValue());
        assertEquals(new BigDecimal("20.00"), summary.getCostBasis());
        assertEquals(new BigDecimal("2.00"), summary.getUnrealisedPnl());
        assertEquals(new BigDecimal("2.00"), summary.getRealisedPnl());
        assertEquals(new BigDecimal("122.00"), summary.getTotalValue());
    }
}
