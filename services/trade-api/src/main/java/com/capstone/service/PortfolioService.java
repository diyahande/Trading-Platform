package com.capstone.service;

import com.capstone.dto.FilledOrderRow;
import com.capstone.dto.HealthDependencyDto;
import com.capstone.dto.HealthResponseDto;
import com.capstone.dto.PnlResponseDto;
import com.capstone.dto.PortfolioHoldingRow;
import com.capstone.dto.PortfolioSummaryDto;
import com.capstone.dto.PricedPositionDto;
import com.capstone.dto.SymbolPnlDto;
import com.capstone.entity.Account;
import com.capstone.enums.OrderSide;
import com.capstone.exception.AccountNotFoundException;
import com.capstone.exception.InvalidInputException;
import com.capstone.exception.MarketDataUnavailableException;
import com.capstone.mapper.PortfolioMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final AccountService accountService;
    private final PortfolioMapper portfolioMapper;
    private final FauxnanceBatchQuoteService fauxnanceBatchQuoteService;
    private final JdbcTemplate jdbcTemplate;

    public PortfolioSummaryDto getPortfolioSummary(String accountRef) {
        Account account = getExistingAccountByReference(accountRef);
        Long accountId = account.getAccountId();
        OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);

        List<PricedPositionDto> pricedPositions = getPricedPositionsInternal(account, null);

        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;
        BigDecimal unrealisedPnl = BigDecimal.ZERO;
        boolean partial = false;

        for (PricedPositionDto position : pricedPositions) {
            costBasis = costBasis.add(safe(position.getCostBasis()));
            if (position.getMarketValue() == null || position.getUnrealisedPnl() == null) {
                partial = true;
                continue;
            }
            marketValue = marketValue.add(position.getMarketValue());
            unrealisedPnl = unrealisedPnl.add(position.getUnrealisedPnl());
        }

        BigDecimal realisedPnl = computeRealisedPnl(accountId, null, null).total;
        BigDecimal totalValue = safe(account.getCashBalance()).add(marketValue);
        BigDecimal unrealisedPercent = null;
        if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
            unrealisedPercent = unrealisedPnl
                    .divide(costBasis, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return new PortfolioSummaryDto(
                account.getAccountReference(),
                account.getCurrency(),
                scaleMoney(account.getCashBalance()),
                scaleMoney(marketValue),
                scaleMoney(costBasis),
                scaleMoney(unrealisedPnl),
                scalePercent(unrealisedPercent),
                scaleMoney(realisedPnl),
                scaleMoney(totalValue),
                pricedPositions.size(),
                partial,
                asOf
        );
    }

    public List<PricedPositionDto> getPricedPositions(String accountRef, String symbolFilter) {
        Account account = getExistingAccountByReference(accountRef);
        return getPricedPositionsInternal(account, symbolFilter);
    }

    public PnlResponseDto getPnl(String accountRef, LocalDate from, LocalDate to, boolean bySymbol) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidInputException();
        }

        Account account = getExistingAccountByReference(accountRef);
        Long accountId = account.getAccountId();
        OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);

        List<PricedPositionDto> pricedPositions = getPricedPositionsInternal(account, null);

        BigDecimal unrealisedTotal = BigDecimal.ZERO;
        Map<String, BigDecimal> unrealisedBySymbol = new HashMap<>();

        for (PricedPositionDto position : pricedPositions) {
            BigDecimal unrealised = safe(position.getUnrealisedPnl());
            unrealisedTotal = unrealisedTotal.add(unrealised);
            unrealisedBySymbol.put(position.getSymbol(), unrealisedBySymbol.getOrDefault(position.getSymbol(), BigDecimal.ZERO).add(unrealised));
        }

        RealisedPnlAggregation realised = computeRealisedPnl(accountId, from, to);
        BigDecimal totalPnl = realised.total.add(unrealisedTotal);

        List<SymbolPnlDto> bySymbolPayload = null;
        if (bySymbol) {
            bySymbolPayload = new ArrayList<>();
            Map<String, BigDecimal> mergedRealised = realised.bySymbol;
            Map<String, BigDecimal> allSymbols = new LinkedHashMap<>();
            for (Map.Entry<String, BigDecimal> e : mergedRealised.entrySet()) {
                allSymbols.put(e.getKey(), e.getValue());
            }
            for (Map.Entry<String, BigDecimal> e : unrealisedBySymbol.entrySet()) {
                allSymbols.putIfAbsent(e.getKey(), BigDecimal.ZERO);
            }

            for (String symbol : allSymbols.keySet()) {
                BigDecimal realisedValue = mergedRealised.getOrDefault(symbol, BigDecimal.ZERO);
                BigDecimal unrealisedValue = unrealisedBySymbol.getOrDefault(symbol, BigDecimal.ZERO);
                bySymbolPayload.add(new SymbolPnlDto(
                        symbol,
                        scaleMoney(realisedValue),
                        scaleMoney(unrealisedValue),
                        scaleMoney(realisedValue.add(unrealisedValue))
                ));
            }
        }

        return new PnlResponseDto(
                account.getAccountReference(),
                account.getCurrency(),
                from,
                to,
                scaleMoney(realised.total),
                scaleMoney(unrealisedTotal),
                scaleMoney(totalPnl),
                bySymbolPayload,
                asOf
        );
    }

    public HealthResponseDto getHealth() {
        OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);

        String postgresStatus = isPostgresReachable() ? "ok" : "down";
        Integer quotaRemaining = fauxnanceBatchQuoteService.getQuotaRemaining();
        String fauxnanceStatus = quotaRemaining == null ? "degraded" : "ok";

        List<HealthDependencyDto> dependencies = List.of(
                new HealthDependencyDto("postgres", postgresStatus, null),
                new HealthDependencyDto("fauxnance", fauxnanceStatus, quotaRemaining)
        );

        boolean healthy = dependencies.stream().allMatch(dep -> "ok".equals(dep.getStatus()));
        return new HealthResponseDto(healthy ? "ok" : "degraded", dependencies, asOf);
    }

    private boolean isPostgresReachable() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception ex) {
            return false;
        }
    }

    private List<PricedPositionDto> getPricedPositionsInternal(Account account, String symbolFilter) {
        List<PortfolioHoldingRow> holdings = portfolioMapper.findOpenHoldingsByAccountId(account.getAccountId());
        if (symbolFilter != null && !symbolFilter.isBlank()) {
            String normalized = symbolFilter.trim().toUpperCase();
            holdings = holdings.stream()
                    .filter(h -> h.getSymbol() != null && h.getSymbol().equalsIgnoreCase(normalized))
                    .toList();
        }

        List<String> symbols = holdings.stream().map(PortfolioHoldingRow::getSymbol).toList();
        Map<String, FauxnanceBatchQuoteService.QuoteResult> quotes = fauxnanceBatchQuoteService.getQuotes(symbols);

        if (!holdings.isEmpty() && quotes.values().stream().noneMatch(FauxnanceBatchQuoteService.QuoteResult::hasPrice)) {
            throw new MarketDataUnavailableException("Pricing unavailable");
        }

        List<PricedPositionDto> result = new ArrayList<>();
        for (PortfolioHoldingRow holding : holdings) {
            BigDecimal quantity = BigDecimal.valueOf(holding.getQuantity());
            BigDecimal averageCost = safe(holding.getAverageCost());
            BigDecimal costBasis = averageCost.multiply(quantity);

            FauxnanceBatchQuoteService.QuoteResult quote = quotes.getOrDefault(
                    holding.getSymbol().toUpperCase(),
                    FauxnanceBatchQuoteService.QuoteResult.unavailable());

            BigDecimal lastPrice = null;
            BigDecimal marketValue = null;
            BigDecimal unrealisedPnl = null;
            BigDecimal unrealisedPnlPercent = null;
            OffsetDateTime priceAsOf = null;
            boolean stale = true;

            if (quote.hasPrice()) {
                lastPrice = scaleMoney(quote.lastPrice());
                marketValue = scaleMoney(quote.lastPrice().multiply(quantity));
                unrealisedPnl = scaleMoney(marketValue.subtract(costBasis));
                if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                    unrealisedPnlPercent = scalePercent(
                            unrealisedPnl.divide(costBasis, 8, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                    );
                }
                priceAsOf = quote.asOf();
                stale = quote.stale();
            }

            result.add(new PricedPositionDto(
                    account.getAccountReference(),
                    holding.getSymbol(),
                    holding.getQuantity(),
                    scaleMoney(averageCost),
                    scaleMoney(costBasis),
                    lastPrice,
                    marketValue,
                    unrealisedPnl,
                    unrealisedPnlPercent,
                    holding.getCurrency(),
                    priceAsOf,
                    stale
            ));
        }

        return result;
    }

    private Account getExistingAccountByReference(String accountRef) {
        if (accountRef == null || accountRef.isBlank()) {
            throw new AccountNotFoundException(-1L);
        }
        return accountService.getAccountByReferenceStrict(accountRef.trim());
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scalePercent(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private RealisedPnlAggregation computeRealisedPnl(Long accountId, LocalDate from, LocalDate to) {
        List<FilledOrderRow> fills = portfolioMapper.findFilledOrdersForAccount(accountId);

        Map<String, PositionAccumulator> state = new HashMap<>();
        Map<String, BigDecimal> realisedBySymbol = new LinkedHashMap<>();
        BigDecimal realisedTotal = BigDecimal.ZERO;

        for (FilledOrderRow fill : fills) {
            String symbol = fill.getSymbol();
            if (symbol == null || fill.getQuantity() == null || fill.getQuantity() <= 0 || fill.getExecutedPrice() == null) {
                continue;
            }

            PositionAccumulator acc = state.computeIfAbsent(symbol, s -> new PositionAccumulator());
            BigDecimal qty = BigDecimal.valueOf(fill.getQuantity());
            BigDecimal px = fill.getExecutedPrice();

            if (fill.getSide() == OrderSide.BUY) {
                BigDecimal newQty = acc.quantity.add(qty);
                if (newQty.compareTo(BigDecimal.ZERO) > 0) {
                    acc.averageCost = acc.averageCost.multiply(acc.quantity)
                            .add(px.multiply(qty))
                            .divide(newQty, 8, RoundingMode.HALF_UP);
                    acc.quantity = newQty;
                }
                continue;
            }

            if (fill.getSide() == OrderSide.SELL) {
                BigDecimal sellQty = qty.min(acc.quantity.max(BigDecimal.ZERO));
                BigDecimal realisedTrade = px.subtract(acc.averageCost).multiply(sellQty);

                boolean inDateRange = isWithinRealisedRange(fill.getExecutedOn(), from, to);
                if (inDateRange) {
                    realisedTotal = realisedTotal.add(realisedTrade);
                    realisedBySymbol.put(symbol,
                            realisedBySymbol.getOrDefault(symbol, BigDecimal.ZERO).add(realisedTrade));
                }

                acc.quantity = acc.quantity.subtract(sellQty);
                if (acc.quantity.compareTo(BigDecimal.ZERO) <= 0) {
                    acc.quantity = BigDecimal.ZERO;
                    acc.averageCost = BigDecimal.ZERO;
                }
            }
        }

        return new RealisedPnlAggregation(realisedTotal, realisedBySymbol);
    }

    private boolean isWithinRealisedRange(OffsetDateTime executedOn, LocalDate from, LocalDate to) {
        if (executedOn == null) {
            return from == null && to == null;
        }
        LocalDate d = executedOn.toLocalDate();
        if (from != null && d.isBefore(from)) {
            return false;
        }
        if (to != null && d.isAfter(to)) {
            return false;
        }
        return true;
    }

    private static final class PositionAccumulator {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal averageCost = BigDecimal.ZERO;
    }

    private record RealisedPnlAggregation(BigDecimal total, Map<String, BigDecimal> bySymbol) {
    }
}
