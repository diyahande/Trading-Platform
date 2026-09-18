package com.capstone.executor.service;

import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.OrderPlacedPayload;
import com.capstone.executor.event.TradeEventPayload;
import com.capstone.executor.domain.ExecutionDecision;
import com.capstone.executor.domain.FillRule;
import com.capstone.executor.domain.PriceNormalizer;
import com.capstone.executor.domain.Quote;
import com.capstone.executor.mapper.AccountDto;
import com.capstone.executor.mapper.AccountMapper;
import com.capstone.executor.mapper.InstrumentDto;
import com.capstone.executor.mapper.InstrumentMapper;
import com.capstone.executor.mapper.OrderDto;
import com.capstone.executor.mapper.OrderMapper;
import com.capstone.executor.mapper.PortfolioDto;
import com.capstone.executor.mapper.PortfolioHoldingsDto;
import com.capstone.executor.mapper.PortfolioHoldingsMapper;
import com.capstone.executor.mapper.PortfolioMapper;
import com.capstone.executor.mapper.TransactionMapper;
import com.capstone.executor.client.FauxnanceQuoteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Core order execution service: orchestrates load-check-fetch-decide-settle-publish workflow
@Service
public class OrderExecutionService {

    private static final Logger logger =
            LoggerFactory.getLogger(OrderExecutionService.class);

    private final FauxnanceQuoteClient quoteClient;
    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final PortfolioMapper portfolioMapper;
    private final PortfolioHoldingsMapper portfolioHoldingsMapper;
    private final TransactionMapper transactionMapper;
    private final TradeEventPublisher tradeEventPublisher;

    public OrderExecutionService(
            FauxnanceQuoteClient quoteClient,
            OrderMapper orderMapper,
            AccountMapper accountMapper,
            InstrumentMapper instrumentMapper,
            PortfolioMapper portfolioMapper,
            PortfolioHoldingsMapper portfolioHoldingsMapper,
            TransactionMapper transactionMapper,
            TradeEventPublisher tradeEventPublisher) {

        this.quoteClient = quoteClient;
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.portfolioMapper = portfolioMapper;
        this.portfolioHoldingsMapper = portfolioHoldingsMapper;
        this.transactionMapper = transactionMapper;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    // Main execution flow for ORDER_PLACED event
    public void executeOrder(EventEnvelope<OrderPlacedPayload> orderEvent) {

        OrderPlacedPayload payload = orderEvent.getPayload();
        String orderId = payload.getOrderId();

        try {

            // Load order; stop if already resolved
            OrderDto order = orderMapper.findById(orderId);

            if (order == null) {

                logger.warn(
                        "Order not found: orderId={}",
                        orderId
                );

                /*
                 * IMPORTANT:
                 * Throw the exception so that the Kafka consumer
                 * can classify it as ORDER_NOT_FOUND and send the
                 * original message to the DLT.
                 */
                throw new IllegalStateException(
                        "Order not found: orderId=" + orderId
                );
            }

            if (!"NEW".equals(order.getStatus())) {

                logger.info(
                        "Order already resolved: orderId={} status={}",
                        orderId,
                        order.getStatus()
                );

                return;
            }

            // Re-check account ACTIVE status (Sprint 5 rule 2)
            AccountDto account =
                    accountMapper.findById(payload.getAccountId());

            if (account == null || !account.isActive()) {

                logger.info(
                        "Account not active: orderId={} accountId={}",
                        orderId,
                        payload.getAccountId()
                );

                rejectOrder(
                        orderId,
                        payload,
                        "ACCOUNT_NOT_ACTIVE"
                );

                return;
            }

            // Re-check instrument tradable (Sprint 5 rule 3)
            InstrumentDto instrument =
                    instrumentMapper.findBySymbol(payload.getSymbol());

            if (instrument == null || !instrument.isTradable()) {

                logger.info(
                        "Instrument not tradable: orderId={} symbol={}",
                        orderId,
                        payload.getSymbol()
                );

                rejectOrder(
                        orderId,
                        payload,
                        "INSTRUMENT_NOT_TRADABLE"
                );

                return;
            }

            // Fetch live quote
            Quote quote =
                    quoteClient.fetchQuote(payload.getSymbol());

            // Apply pure fill rule
            ExecutionDecision decision =
                    FillRule.decide(
                            payload.getSide(),
                            payload.getPrice(),
                            quote
                    );

            if (decision.isFill()) {

                // Re-check execution-time funds for BUY (Sprint 5 rule 6)
                if ("BUY".equalsIgnoreCase(payload.getSide())) {

                    BigDecimal totalCost =
                            decision
                                    .getExecutionPrice()
                                    .multiply(
                                            BigDecimal.valueOf(
                                                    payload.getQuantity()
                                            )
                                    );

                    if (account.getCashBalance()
                            .compareTo(totalCost) < 0) {

                        logger.info(
                                "Insufficient funds at execution: "
                                        + "orderId={} required={} available={}",
                                orderId,
                                totalCost,
                                account.getCashBalance()
                        );

                        rejectOrder(
                                orderId,
                                payload,
                                "INSUFFICIENT_FUNDS"
                        );

                        return;
                    }
                }

                // Fill the order transactionally
                fillOrder(
                        orderId,
                        payload,
                        decision.getExecutionPrice(),
                        account,
                        instrument
                );

            } else {

                // Reject: order cannot be left at NEW forever
                rejectOrder(
                        orderId,
                        payload,
                        decision.getRejectReason()
                );
            }

        } catch (Exception e) {

            logger.error(
                    "Execution error: orderId={} error={}",
                    orderId,
                    e.getMessage(),
                    e
            );

            /*
             * IMPORTANT:
             *
             * Let ORDER_NOT_FOUND reach the Kafka consumer so
             * FailureClassifier can classify it as permanent
             * and send it to orders.DLT.
             *
             * Also rethrow exhausted retry errors from optimistic locking.
             * 
             * Other exceptions keep the previous SYSTEM_ERROR
             * behavior.
             */
            if (e instanceof IllegalStateException
                    && e.getMessage() != null
                    && (e.getMessage().contains("Order not found")
                        || e.getMessage().contains("Exhausted optimistic lock retries"))) {

                throw e;
            }

            try {
                rejectOrder(
                        orderId,
                        payload,
                        "SYSTEM_ERROR"
                );
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Fill order with atomic settlement: status change, cash movement, and position write
     * all happen in one transaction, guarded by optimistic locks on account and position versions.
     * 
     * Acceptance Criteria:
     * 1. All three writes (order status, cash, position) happen together or not at all
     * 2. Guarded state transition (WHERE status='NEW') is first write - idempotent
     * 3. Optimistic lock on account version with bounded retry attempts
     * 4. Zero rows affected on optimistic lock failure -> re-read and retry
     * 5. Event published AFTER transaction commit, offset acknowledged AFTER event publish
     */
    @Transactional
    private void fillOrder(
            String orderId,
            OrderPlacedPayload payload,
            BigDecimal executionPrice,
            AccountDto account,
            InstrumentDto instrument) {

        BigDecimal normalizedPrice = PriceNormalizer.normalize(executionPrice);
        Instant now = Instant.now();

        /**
         * WRITE 1: Guarded state transition
         * First write in transaction: conditional on order still being NEW.
         * Zero rows affected means another delivery got there first -> return without publishing.
         */
        int updated = orderMapper.updateToFilled(orderId, normalizedPrice, now);

        if (updated == 0) {
            logger.info(
                    "Order already resolved (idempotent delivery): orderId={}",
                    orderId
            );
            return;
        }

        logger.debug("Order status updated: orderId={}", orderId);

        // Calculate cash delta (negative for BUY, positive for SELL)
        BigDecimal quantity = BigDecimal.valueOf(payload.getQuantity());
        BigDecimal cashDelta = normalizedPrice.multiply(quantity);
        if ("BUY".equalsIgnoreCase(payload.getSide())) {
            cashDelta = cashDelta.negate();
        }

        /**
         * WRITES 2 & 3: Optimistic lock retry loop
         * 
         * Account version guard ensures only one delivery successfully moves cash.
         * Portfolio position version guard ensures positions stay consistent.
         * Retry up to 3 attempts if version conflict (another service writing to account).
         * Treat only exhausted budget as error, not version conflicts.
         */
        final int MAX_RETRIES = 3;
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            attempt++;

            // Re-read account to get current version
            AccountDto currentAccount = accountMapper.findById(payload.getAccountId());
            if (currentAccount == null || !currentAccount.isActive()) {
                throw new IllegalStateException(
                        "Account became inactive during settlement: orderId=" + orderId
                );
            }

            logger.debug(
                    "Optimistic lock attempt {}: orderId={} accountVersion={}",
                    attempt,
                    orderId,
                    currentAccount.getVersion()
            );

            /**
             * WRITE 2: Update account cash with version guard
             * Only succeeds if version matches - detects concurrent modifications.
             * Zero rows affected -> version conflict, re-read and retry.
             */
            int cashUpdated = accountMapper.updateCashWithVersionGuard(
                    payload.getAccountId(),
                    cashDelta,
                    currentAccount.getVersion()
            );

            if (cashUpdated == 0) {
                logger.debug(
                        "Account version conflict (attempt {}): orderId={} retrying",
                        attempt,
                        orderId
                );
                if (attempt < MAX_RETRIES) {
                    continue; // Retry
                } else {
                    throw new IllegalStateException(
                            "Exhausted optimistic lock retries for account cash update: "
                                    + "orderId=" + orderId + " attempts=" + MAX_RETRIES
                    );
                }
            }

            logger.debug("Account cash updated: orderId={} delta={}", orderId, cashDelta);

            /**
             * WRITE 3: Update portfolio position with version guard
             * Similar optimistic lock: only succeeds if version matches.
             * Creates new position if it doesn't exist.
             */
            PortfolioDto portfolio = portfolioMapper.findByAccountId(payload.getAccountId());
            if (portfolio == null) {
                throw new IllegalStateException(
                        "Portfolio not found for account: orderId=" + orderId
                );
            }

            PortfolioHoldingsDto holding = portfolioHoldingsMapper.findByPortfolioAndInstrument(
                    portfolio.getPortfolioId(),
                    instrument.getInstrumentId()
            );

            if (holding == null) {
                // New position: create it
                if ("BUY".equalsIgnoreCase(payload.getSide())) {
                    portfolioHoldingsMapper.insert(
                            portfolio.getPortfolioId(),
                            instrument.getInstrumentId(),
                            quantity,
                            normalizedPrice
                    );
                    logger.debug(
                            "New position created: orderId={} symbol={} qty={}",
                            orderId,
                            payload.getSymbol(),
                            quantity
                    );
                } else {
                    // SELL on non-existent position - should not happen, but log it
                    logger.warn(
                            "SELL order on non-existent position: orderId={} symbol={}",
                            orderId,
                            payload.getSymbol()
                    );
                }
            } else {
                // Existing position: update with new quantity and average price
                BigDecimal newQuantity = holding.getQuantity();
                BigDecimal newAveragePrice = holding.getAveragePrice();

                if ("BUY".equalsIgnoreCase(payload.getSide())) {
                    // Update average price: weighted by quantity
                    BigDecimal totalCost = holding.getAveragePrice()
                            .multiply(holding.getQuantity())
                            .add(normalizedPrice.multiply(quantity));
                    newQuantity = holding.getQuantity().add(quantity);
                    newAveragePrice = totalCost.divide(newQuantity,
                            8, java.math.RoundingMode.HALF_UP);
                } else {
                    // SELL: reduce quantity
                    newQuantity = holding.getQuantity().subtract(quantity);
                    if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalStateException(
                                "Insufficient position to sell: orderId=" + orderId
                        );
                    }
                    // Average price stays the same on SELL
                }

                int positionUpdated = portfolioHoldingsMapper.update(
                        portfolio.getPortfolioId(),
                        instrument.getInstrumentId(),
                        newQuantity,
                        newAveragePrice
                );

                if (positionUpdated == 0) {
                    throw new IllegalStateException(
                            "Position not found or update failed: orderId=" + orderId
                    );
                }

                logger.debug(
                        "Position updated: orderId={} symbol={} newQty={}",
                        orderId,
                        payload.getSymbol(),
                        newQuantity
                );
            }

            /**
             * WRITE 4: Record transaction
             * Creates an immutable record of the trade execution.
             * This is part of the atomic settlement transaction.
             */
            transactionMapper.insert(
                    orderId,
                    instrument.getInstrumentId(),
                    quantity,
                    normalizedPrice,
                    "COMPLETED"
            );

            logger.debug(
                    "Transaction recorded: orderId={} instrumentId={} qty={} price={}",
                    orderId,
                    instrument.getInstrumentId(),
                    quantity,
                    normalizedPrice
            );

            // Success: all four writes completed in one transaction
            logger.info(
                    "Order settled successfully: orderId={} price={} side={} qty={}",
                    orderId,
                    normalizedPrice,
                    payload.getSide(),
                    quantity
            );

            // Publish event AFTER transaction commits (Spring handles this with @Transactional)
            publishTradeEvent(
                    payload,
                    "ORDER_FILLED",
                    normalizedPrice,
                    null,
                    cashDelta,
                    holding != null ? holding.getQuantity().intValue() : quantity.intValue(),
                    holding != null ? holding.getAveragePrice() : normalizedPrice,
                    now
            );

            return; // Exit retry loop on success
        }

        // Should not reach here due to throw in loop, but just in case
        throw new IllegalStateException(
                "Order settlement failed with exhausted retries: orderId=" + orderId
        );
    }

    // Reject order transactionally: update DB then publish event
    @Transactional
    private void rejectOrder(
            String orderId,
            OrderPlacedPayload payload,
            String reason) {

        // Guarded update: returns 1 if successful, 0 if not NEW (idempotent)
        int updated =
                orderMapper.updateToRejected(
                        orderId,
                        reason
                );

        if (updated == 0) {

            logger.info(
                    "Order already resolved (idempotent): orderId={}",
                    orderId
            );

            return;
        }

        // Publish rejection event after DB commit
        publishTradeEvent(
                payload,
                "ORDER_REJECTED",
                null,
                reason,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Instant.now()
        );

        logger.info(
                "Order rejected: orderId={} reason={}",
                orderId,
                reason
        );
    }

    // Publish event to trade-events topic keyed by accountId
    private void publishTradeEvent(
            OrderPlacedPayload payload,
            String eventType,
            BigDecimal executedPrice,
            String reason,
            BigDecimal cashDelta,
            Integer positionQuantity,
            BigDecimal avgCost,
            Instant executedOn) {

        TradeEventPayload tradeEvent =
                new TradeEventPayload(
                        payload.getOrderId(),
                        payload.getAccountId(),
                        payload.getSymbol(),
                        payload.getSide(),
                        payload.getQuantity(),
                        payload.getPrice(),
                        executedPrice,
                        eventType.equals("ORDER_FILLED")
                                ? "FILLED"
                                : "REJECTED",
                        reason,
                        cashDelta,
                        positionQuantity,
                        avgCost,
                        executedOn
                );

        EventEnvelope<TradeEventPayload> event =
                new EventEnvelope<>(
                        UUID.randomUUID().toString(),
                        eventType,
                        Instant.now(),
                        "trade-executor",
                        1,
                        tradeEvent
                );

        tradeEventPublisher.publish(event);
    }
}