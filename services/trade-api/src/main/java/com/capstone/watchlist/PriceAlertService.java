package com.capstone.watchlist;

import com.capstone.dto.CreatePriceAlertRequest;
import com.capstone.dto.PriceAlertResponse;
import com.capstone.entity.Account;
import com.capstone.entity.PriceAlert;
import com.capstone.entity.Watchlist;
import com.capstone.exception.AccountNotAccessibleException;
import com.capstone.exception.AlertLimitExceededException;
import com.capstone.exception.AlertResourceNotFoundException;
import com.capstone.exception.InvalidInputException;
import com.capstone.mapper.PriceAlertMapper;
import com.capstone.mapper.WatchlistMapper;
import com.capstone.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private static final Logger logger = LoggerFactory.getLogger(PriceAlertService.class);
    private static final int MAX_ALERTS_PER_ACCOUNT = 100;

    private final PriceAlertMapper priceAlertMapper;
    private final WatchlistMapper watchlistMapper;
    private final AccountService accountService;
    private final AlertNotificationPort alertNotificationPort;

    /**
     * Create a price alert for a watchlist item.
     * Enforces: cap per account, valid direction.
     */
    public PriceAlertResponse createAlert(
            String accountReference,
            CreatePriceAlertRequest request
    ) {
        validateCreateAlertRequest(request);

        // Authorize: account owns watchlist
        Account account = accountService.getAccountByReferenceStrict(accountReference);
        Long userId = account.getUserId();

        // Verify watchlist exists and belongs to user
        Watchlist watchlist = watchlistMapper.findById(request.getWatchlistId());
        if (watchlist == null) {
            throw new AlertResourceNotFoundException("Watchlist not found");
        }
        if (!watchlist.getUserId().equals(userId)) {
            throw new AccountNotAccessibleException();
        }

        // Enforce cap
        int activeAlertCount = priceAlertMapper.countActiveByUserId(userId);
        if (activeAlertCount >= MAX_ALERTS_PER_ACCOUNT) {
            throw new AlertLimitExceededException(MAX_ALERTS_PER_ACCOUNT);
        }

        // Validate direction
        PriceAlert.Direction direction;
        try {
            direction = PriceAlert.Direction.valueOf(request.getDirection().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException();
        }

        // Create alert
        Instant now = Instant.now();
        PriceAlert alert = new PriceAlert(
                null,
                request.getWatchlistId(),
                request.getThreshold(),
                direction,
                PriceAlert.Status.ACTIVE,
                now,
                null,
                now
        );
        priceAlertMapper.insert(alert);

        logger.info(
                "Price alert created: alertId={} userId={} watchlistId={} threshold={} direction={}",
                alert.getAlertId(),
                userId,
                request.getWatchlistId(),
                request.getThreshold(),
                direction
        );

        return mapToResponse(alert);
    }

    /**
     * Get all alerts for a specific watchlist.
     * Authorizes that the watchlist belongs to the caller.
     */
    public List<PriceAlertResponse> getAlertsForWatchlist(
            String accountReference,
            Long watchlistId
    ) {
        Account account = accountService.getAccountByReferenceStrict(accountReference);
        Long userId = account.getUserId();

        // Verify watchlist belongs to user
        Watchlist watchlist = watchlistMapper.findById(watchlistId);
        if (watchlist == null) {
            throw new AlertResourceNotFoundException("Watchlist not found");
        }
        if (!watchlist.getUserId().equals(userId)) {
            throw new AccountNotAccessibleException();
        }

        List<PriceAlert> alerts = priceAlertMapper.findByWatchlistId(watchlistId);
        List<PriceAlertResponse> responses = new ArrayList<>();
        for (PriceAlert alert : alerts) {
            responses.add(mapToResponse(alert));
        }
        return responses;
    }

    /**
     * Get all alerts for the account.
     */
    public List<PriceAlertResponse> getAlertsForAccount(String accountReference) {
        Account account = accountService.getAccountByReferenceStrict(accountReference);
        Long userId = account.getUserId();

        List<Watchlist> watchlists = watchlistMapper.findByUserId(userId);
        List<PriceAlertResponse> responses = new ArrayList<>();

        for (Watchlist watchlist : watchlists) {
            List<PriceAlert> alerts = priceAlertMapper.findByWatchlistId(watchlist.getWatchlistId());
            for (PriceAlert alert : alerts) {
                responses.add(mapToResponse(alert));
            }
        }

        return responses;
    }

    /**
     * Deactivate an alert manually.
     */
    public void deactivateAlert(String accountReference, Long alertId) {
        Account account = accountService.getAccountByReferenceStrict(accountReference);
        Long userId = account.getUserId();

        Optional<PriceAlert> alertOpt = priceAlertMapper.findById(alertId);
        if (alertOpt.isEmpty()) {
            throw new AlertResourceNotFoundException("Alert not found");
        }

        PriceAlert alert = alertOpt.get();

        // Verify user owns this alert (via watchlist)
        Watchlist watchlist = watchlistMapper.findById(alert.getWatchlistId());
        if (watchlist == null) {
            throw new AlertResourceNotFoundException("Watchlist not found");
        }
        if (!watchlist.getUserId().equals(userId)) {
            throw new AccountNotAccessibleException();
        }

        // Update status
        PriceAlert deactivated = new PriceAlert(
                alert.getAlertId(),
                alert.getWatchlistId(),
                alert.getThreshold(),
                alert.getDirection(),
                PriceAlert.Status.DEACTIVATED,
                alert.getCreatedAt(),
                alert.getTriggeredAt(),
                Instant.now()
        );
        priceAlertMapper.updateStatus(deactivated);

        logger.info("Price alert deactivated: alertId={}", alertId);
    }

    /**
     * Evaluate alerts for a symbol when market data arrives.
     * Called by market data consumer.
     * 
     * Decision: After trigger, alert moves to TRIGGERED state.
     * Customer must manually deactivate or reset it.
     */
    public void evaluateAlertsForSymbol(String symbol, BigDecimal currentPrice) {
        List<PriceAlert> activeAlerts = priceAlertMapper.findActiveBySymbol(symbol);

        for (PriceAlert alert : activeAlerts) {
            boolean triggered = checkThreshold(
                    alert.getThreshold(),
                    alert.getDirection(),
                    currentPrice
            );

            if (triggered) {
                try {
                    // Mark as triggered
                    PriceAlert triggeredAlert = new PriceAlert(
                            alert.getAlertId(),
                            alert.getWatchlistId(),
                            alert.getThreshold(),
                            alert.getDirection(),
                            PriceAlert.Status.TRIGGERED,
                            alert.getCreatedAt(),
                            Instant.now(),
                            Instant.now()
                    );
                    priceAlertMapper.updateStatus(triggeredAlert);

                    // Get user ID for notification
                    Watchlist watchlist = watchlistMapper.findById(alert.getWatchlistId());
                    if (watchlist != null) {
                        // Deliver notification
                        try {
                            alertNotificationPort.deliverPriceAlert(
                                    alert.getAlertId(),
                                    watchlist.getUserId(),
                                    symbol,
                                    currentPrice,
                                    alert.getThreshold(),
                                    alert.getDirection().name()
                            );

                            logger.info(
                                    "Price alert triggered and delivered: alertId={} symbol={} price={}",
                                    alert.getAlertId(),
                                    symbol,
                                    currentPrice
                            );
                        } catch (AlertNotificationPort.AlertDeliveryException e) {
                            // Decision: Mark as DELIVERY_FAILED so customer can see delivery failed
                            logger.warn(
                                    "Price alert notification delivery failed: alertId={} reason={}",
                                    alert.getAlertId(),
                                    e.getMessage()
                            );

                            PriceAlert failedAlert = new PriceAlert(
                                    alert.getAlertId(),
                                    alert.getWatchlistId(),
                                    alert.getThreshold(),
                                    alert.getDirection(),
                                    PriceAlert.Status.DELIVERY_FAILED,
                                    alert.getCreatedAt(),
                                    Instant.now(),
                                    Instant.now()
                            );
                            priceAlertMapper.updateStatus(failedAlert);
                        }
                    } else {
                        logger.warn(
                                "Skipping notification for alertId={} because watchlistId={} no longer exists",
                                alert.getAlertId(),
                                alert.getWatchlistId()
                        );
                    }
                } catch (Exception e) {
                    logger.error(
                            "Error evaluating alert: alertId={} symbol={} error={}",
                            alert.getAlertId(),
                            symbol,
                            e.getMessage(),
                            e
                    );
                }
            }
        }
    }

    /**
     * Check if current price crosses the threshold given the direction.
     * ABOVE: triggers when price >= threshold
     * BELOW: triggers when price <= threshold
     */
    private boolean checkThreshold(
            BigDecimal threshold,
            PriceAlert.Direction direction,
            BigDecimal currentPrice
    ) {
        if (direction == PriceAlert.Direction.ABOVE) {
            return currentPrice.compareTo(threshold) >= 0;
        } else {
            return currentPrice.compareTo(threshold) <= 0;
        }
    }

    private PriceAlertResponse mapToResponse(PriceAlert alert) {
        return new PriceAlertResponse(
                alert.getAlertId(),
                alert.getWatchlistId(),
                alert.getThreshold(),
                alert.getDirection().name(),
                alert.getStatus().name(),
                alert.getCreatedAt(),
                alert.getTriggeredAt(),
                alert.getUpdatedAt()
        );
    }

    private void validateCreateAlertRequest(CreatePriceAlertRequest request) {
        if (request == null
                || request.getWatchlistId() == null
                || request.getThreshold() == null
                || request.getDirection() == null
                || request.getDirection().isBlank()
                || request.getThreshold().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException();
        }
    }
}
