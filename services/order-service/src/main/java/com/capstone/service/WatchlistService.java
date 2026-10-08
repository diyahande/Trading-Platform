package com.capstone.service;

import com.capstone.dto.WatchlistItemResponse;
import com.capstone.entity.Account;
import com.capstone.entity.Instrument;
import com.capstone.entity.Watchlist;
import com.capstone.exception.MarketDataUnavailableException;
import com.capstone.mapper.WatchlistMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private static final Logger logger = LoggerFactory.getLogger(WatchlistService.class);

    private final WatchlistMapper watchlistMapper;
    private final AccountService accountService;
    private final InstrumentService instrumentService;
    private final KafkaMarketQuoteService kafkaMarketQuoteService;

    public List<WatchlistItemResponse> getWatchlistForAuthorizedAccount(String accountReference) {
        // Get the account and extract userId
        Account account = accountService.getAccountByReferenceStrict(accountReference);
        Long userId = account.getUserId();

        // Get all watchlist items for this user
        List<Watchlist> watchlistItems = watchlistMapper.findByUserId(userId);
        List<WatchlistItemResponse> responses = new ArrayList<>();

        for (Watchlist item : watchlistItems) {
            try {
                WatchlistItemResponse response = enrichWatchlistItem(item);
                responses.add(response);
            } catch (MarketDataUnavailableException e) {
                logger.debug(
                        "Watchlist quote missing in Kafka cache for watchlistId={}, returning item without market fields: {}",
                        item.getWatchlistId(),
                        e.getMessage()
                );
                responses.add(enrichWatchlistItemWithoutQuote(item));
            } catch (Exception e) {
                logger.warn(
                        "Skipping watchlist item due to enrichment failure: watchlistId={} reason={}",
                        item.getWatchlistId(),
                        e.getMessage()
                );
            }
        }

        return responses;
    }

    public void addToWatchlist(String accountReference, Long instrumentId, String name) {
        // Get the account and extract userId
        Account account = accountService.getAccountByReferenceStrict(accountReference);
        Long userId = account.getUserId();

        // Verify instrument exists
        Instrument instrument = instrumentService.getInstrumentById(instrumentId)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found"));

        // Create and insert watchlist item
        Watchlist watchlist = new Watchlist(
                null,
                userId,
                instrumentId,
                name,
                java.time.Instant.now()
        );
        watchlistMapper.insert(watchlist);
    }

    public void removeFromWatchlist(String accountReference, Long watchlistId) {
        // Get the account for authorization
        Account account = accountService.getAccountByReferenceStrict(accountReference);

        // Verify the watchlist item belongs to the user
        Watchlist watchlist = watchlistMapper.findById(watchlistId);
        if (watchlist == null || !watchlist.getUserId().equals(account.getUserId())) {
            throw new IllegalArgumentException("Watchlist item not found or does not belong to user");
        }

        watchlistMapper.deleteById(watchlistId);
    }

    private WatchlistItemResponse enrichWatchlistItem(Watchlist watchlist) {
        // Get instrument details
        Instrument instrument = instrumentService.getInstrumentById(watchlist.getInstrumentId())
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found"));

        // Get current market quote from Kafka-backed cache
        KafkaMarketQuoteService.QuoteSnapshot quote = kafkaMarketQuoteService.getQuote(instrument.getSymbol());

        // Calculate price change
        BigDecimal change = quote.price().subtract(quote.previousClose());
        BigDecimal changePercent = quote.previousClose().signum() != 0
                ? change.divide(quote.previousClose(), 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return new WatchlistItemResponse(
                watchlist.getWatchlistId(),
                instrument.getSymbol(),
                instrument.getDisplayName(),
                instrument.getAssetClass().name(),
                quote.price(),
                change,
                changePercent,
                watchlist.getCreatedAt()
        );
    }

    private WatchlistItemResponse enrichWatchlistItemWithoutQuote(Watchlist watchlist) {
        Instrument instrument = instrumentService.getInstrumentById(watchlist.getInstrumentId())
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found"));

        return new WatchlistItemResponse(
                watchlist.getWatchlistId(),
                instrument.getSymbol(),
                instrument.getDisplayName(),
                instrument.getAssetClass().name(),
                null,
                null,
                null,
                watchlist.getCreatedAt()
        );
    }
}
