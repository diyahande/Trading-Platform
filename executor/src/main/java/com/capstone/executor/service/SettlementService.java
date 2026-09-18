package com.capstone.executor.service;

import com.capstone.executor.domain.PriceNormalizer;
import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.OrderPlacedPayload;
import com.capstone.executor.event.TradeEventPayload;
import com.capstone.executor.mapper.AccountDto;
import com.capstone.executor.mapper.AccountMapper;
import com.capstone.executor.mapper.InstrumentDto;
import com.capstone.executor.mapper.OrderMapper;
import com.capstone.executor.mapper.PortfolioDto;
import com.capstone.executor.mapper.PortfolioHoldingsDto;
import com.capstone.executor.mapper.PortfolioHoldingsMapper;
import com.capstone.executor.mapper.PortfolioMapper;
import com.capstone.executor.mapper.TransactionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Handles transactional settlement of orders.
 *
 * This class is responsible ONLY for database settlement.
 *
 * Kafka publication is intentionally NOT done inside this class.
 *
 * Flow:
 *
 * OrderExecutionService
 *        |
 *        v
 * SettlementService.fillOrder()
 *        |
 *        v
 * Database transaction
 *        |
 *        v
 * COMMIT
 *        |
 *        v
 * return TradeEvent to OrderExecutionService
 *        |
 *        v
 * publish to Kafka
 */
@Service
public class SettlementService {

    private static final Logger logger =
            LoggerFactory.getLogger(SettlementService.class);

    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final PortfolioMapper portfolioMapper;
    private final PortfolioHoldingsMapper portfolioHoldingsMapper;
    private final TransactionMapper transactionMapper;

    public SettlementService(
            OrderMapper orderMapper,
            AccountMapper accountMapper,
            PortfolioMapper portfolioMapper,
            PortfolioHoldingsMapper portfolioHoldingsMapper,
            TransactionMapper transactionMapper) {

        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.portfolioMapper = portfolioMapper;
        this.portfolioHoldingsMapper = portfolioHoldingsMapper;
        this.transactionMapper = transactionMapper;
    }

    /**
     * Settles a filled order.
     *
     * IMPORTANT:
     *
     * Because this method is public and belongs to a separate
     * Spring bean, @Transactional is actually applied.
     *
     * All database changes happen in one transaction:
     *
     * 1. Order -> FILLED
     * 2. Account cash update
     * 3. Portfolio position update
     * 4. Transaction record
     *
     * If anything fails, the entire transaction rolls back.
     *
     * After the transaction commits successfully, this method
     * returns the TradeEvent to the caller.
     */
    @Transactional
    public EventEnvelope<TradeEventPayload> fillOrder(
            String orderId,
            OrderPlacedPayload payload,
            BigDecimal executionPrice,
            InstrumentDto instrument) {

        logger.info(
                "fillOrder() START: orderId={} symbol={} side={} qty={} executionPrice={} instrumentId={}",
                orderId,
                payload.getSymbol(),
                payload.getSide(),
                payload.getQuantity(),
                executionPrice,
                instrument.getInstrumentId()
        );

        BigDecimal normalizedPrice =
                PriceNormalizer.normalize(executionPrice);

        logger.info(
                "Price normalized: orderId={} executionPrice={} normalizedPrice={}",
                orderId,
                executionPrice,
                normalizedPrice
        );

        Instant executedOn = Instant.now();

        /*
         * ---------------------------------------------------------
         * 1. Guarded order state transition
         * ---------------------------------------------------------
         *
         * Only NEW orders can become FILLED.
         *
         * If updated == 0, the order was already processed.
         */
        int updated =
                orderMapper.updateToFilled(
                        orderId,
                        normalizedPrice,
                        executedOn
                );

        if (updated == 0) {

            logger.info(
                    "Order already resolved (idempotent delivery): orderId={}",
                    orderId
            );

            return null;
        }

        logger.info(
                "Order status changed to FILLED: orderId={} price={}",
                orderId,
                normalizedPrice
        );

        BigDecimal quantity =
                BigDecimal.valueOf(payload.getQuantity());

        /*
         * ---------------------------------------------------------
         * 2. Calculate cash movement
         * ---------------------------------------------------------
         *
         * BUY  -> negative cash
         * SELL -> positive cash
         */
        BigDecimal cashDelta =
                normalizedPrice.multiply(quantity);

        if ("BUY".equalsIgnoreCase(payload.getSide())) {
            cashDelta = cashDelta.negate();
        }

        /*
         * ---------------------------------------------------------
         * 3. Account settlement
         * ---------------------------------------------------------
         */
        final int MAX_RETRIES = 3;

        BigDecimal positionQuantityAfter = BigDecimal.ZERO;
        BigDecimal averageCostAfter = BigDecimal.ZERO;

        boolean settlementSuccessful = false;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

            AccountDto currentAccount =
                    accountMapper.findById(
                            payload.getAccountId()
                    );

            if (currentAccount == null) {

                throw new IllegalStateException(
                        "Account not found during settlement: orderId="
                                + orderId
                );
            }

            /*
             * Account may have been suspended after the order
             * was accepted but before execution.
             */
            if (!currentAccount.isActive()) {

                throw new IllegalStateException(
                        "Account became inactive during settlement: orderId="
                                + orderId
                );
            }

            /*
             * -----------------------------------------------------
             * Execution-time BUY balance check
             * -----------------------------------------------------
             */
            if ("BUY".equalsIgnoreCase(payload.getSide())) {

                BigDecimal required =
                        normalizedPrice.multiply(quantity);

                if (currentAccount.getCashBalance()
                        .compareTo(required) < 0) {

                    throw new IllegalStateException(
                            "Insufficient funds at settlement: orderId="
                                    + orderId
                                    + " required=" + required
                                    + " available="
                                    + currentAccount.getCashBalance()
                    );
                }
            }

            logger.debug(
                    "Settlement attempt {}: orderId={} accountVersion={}",
                    attempt,
                    orderId,
                    currentAccount.getVersion()
            );

            /*
             * -----------------------------------------------------
             * Update account cash using optimistic locking
             * -----------------------------------------------------
             */
            int cashUpdated =
                    accountMapper.updateCashWithVersionGuard(
                            payload.getAccountId(),
                            cashDelta,
                            currentAccount.getVersion()
                    );

            if (cashUpdated == 0) {

                logger.warn(
                        "Account version conflict: orderId={} attempt={}",
                        orderId,
                        attempt
                );

                if (attempt == MAX_RETRIES) {

                    throw new IllegalStateException(
                            "Exhausted optimistic lock retries "
                                    + "for account cash update: orderId="
                                    + orderId
                    );
                }

                continue;
            }

            logger.debug(
                    "Account cash updated: orderId={} delta={}",
                    orderId,
                    cashDelta
            );

            /*
             * -----------------------------------------------------
             * 4. Load portfolio
             * -----------------------------------------------------
             */
            PortfolioDto portfolio =
                    portfolioMapper.findByAccountId(
                            payload.getAccountId()
                    );

            if (portfolio == null) {

                throw new IllegalStateException(
                        "Portfolio not found for account: orderId="
                                + orderId
                );
            }

            /*
             * -----------------------------------------------------
             * 5. Load current holding
             * -----------------------------------------------------
             */
            PortfolioHoldingsDto holding =
                    portfolioHoldingsMapper
                            .findByPortfolioAndInstrument(
                                    portfolio.getPortfolioId(),
                                    instrument.getInstrumentId()
                            );

            /*
             * -----------------------------------------------------
             * 6. BUY
             * -----------------------------------------------------
             */
            if ("BUY".equalsIgnoreCase(payload.getSide())) {

                if (holding == null) {

                    /*
                     * New position.
                     */
                    portfolioHoldingsMapper.insert(
                            portfolio.getPortfolioId(),
                            instrument.getInstrumentId(),
                            quantity,
                            normalizedPrice
                    );

                    positionQuantityAfter = quantity;
                    averageCostAfter = normalizedPrice;

                    logger.debug(
                            "New position created: orderId={} "
                                    + "symbol={} qty={} avgPrice={}",
                            orderId,
                            payload.getSymbol(),
                            quantity,
                            normalizedPrice
                    );

                } else {

                    /*
                     * Existing position.
                     *
                     * Weighted average:
                     *
                     * oldQty * oldAvg
                     * +
                     * newQty * newPrice
                     * -------------------
                     * totalQty
                     */
                    BigDecimal oldQuantity =
                            holding.getQuantity();

                    BigDecimal oldAveragePrice =
                            holding.getAveragePrice();

                    BigDecimal totalCost =
                            oldAveragePrice
                                    .multiply(oldQuantity)
                                    .add(
                                            normalizedPrice
                                                    .multiply(quantity)
                                    );

                    BigDecimal newQuantity =
                            oldQuantity.add(quantity);

                    BigDecimal newAveragePrice =
                            totalCost.divide(
                                    newQuantity,
                                    8,
                                    java.math.RoundingMode.HALF_UP
                            );

                    int positionUpdated =
                            portfolioHoldingsMapper.update(
                                    portfolio.getPortfolioId(),
                                    instrument.getInstrumentId(),
                                    newQuantity,
                                    newAveragePrice
                            );

                    if (positionUpdated == 0) {

                        throw new IllegalStateException(
                                "Position update failed: orderId="
                                        + orderId
                        );
                    }

                    positionQuantityAfter = newQuantity;
                    averageCostAfter = newAveragePrice;

                    logger.debug(
                            "Existing position updated: orderId={} "
                                    + "symbol={} qty={} avgPrice={}",
                            orderId,
                            payload.getSymbol(),
                            newQuantity,
                            newAveragePrice
                    );
                }
            }

            /*
             * -----------------------------------------------------
             * 7. SELL
             * -----------------------------------------------------
             */
            else if ("SELL".equalsIgnoreCase(payload.getSide())) {

                if (holding == null) {

                    throw new IllegalStateException(
                            "Insufficient position to sell: orderId="
                                    + orderId
                    );
                }

                BigDecimal currentQuantity =
                        holding.getQuantity();

                if (currentQuantity.compareTo(quantity) < 0) {

                    throw new IllegalStateException(
                            "Insufficient position to sell: orderId="
                                    + orderId
                                    + " required=" + quantity
                                    + " available=" + currentQuantity
                    );
                }

                BigDecimal newQuantity =
                        currentQuantity.subtract(quantity);

                BigDecimal averagePrice =
                        holding.getAveragePrice();

                int positionUpdated =
                        portfolioHoldingsMapper.update(
                                portfolio.getPortfolioId(),
                                instrument.getInstrumentId(),
                                newQuantity,
                                averagePrice
                        );

                if (positionUpdated == 0) {

                    throw new IllegalStateException(
                            "Position update failed: orderId="
                                    + orderId
                    );
                }

                positionQuantityAfter = newQuantity;
                averageCostAfter = averagePrice;

                logger.debug(
                        "Position reduced: orderId={} symbol={} qty={}",
                        orderId,
                        payload.getSymbol(),
                        newQuantity
                );
            }

            /*
             * -----------------------------------------------------
             * 8. Transaction record
             * -----------------------------------------------------
             */
            transactionMapper.insert(
                    orderId,
                    instrument.getInstrumentId(),
                    quantity,
                    normalizedPrice,
                    "COMPLETED"
            );

            settlementSuccessful = true;
            break;
        }

        if (!settlementSuccessful) {

            throw new IllegalStateException(
                    "Order settlement failed: orderId="
                            + orderId
            );
        }

        logger.info(
                "Order settled successfully: orderId={} "
                        + "price={} side={} qty={} "
                        + "cashDelta={} positionAfter={} avgCostAfter={}",
                orderId,
                normalizedPrice,
                payload.getSide(),
                quantity,
                cashDelta,
                positionQuantityAfter,
                averageCostAfter
        );

        /*
         * ---------------------------------------------------------
         * 9. Build trade event
         * ---------------------------------------------------------
         *
         * IMPORTANT:
         *
         * We only return this event.
         *
         * Kafka publication happens in OrderExecutionService
         * AFTER this transactional method has committed.
         */
        TradeEventPayload tradeEventPayload =
                new TradeEventPayload(
                        payload.getOrderId(),
                        payload.getAccountId(),
                        payload.getSymbol(),
                        payload.getSide(),
                        payload.getQuantity(),
                        payload.getPrice(),
                        normalizedPrice,
                        "FILLED",
                        null,
                        cashDelta,
                        positionQuantityAfter.intValue(),
                        averageCostAfter,
                        executedOn
                );

        EventEnvelope<TradeEventPayload> resultEvent = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                "ORDER_FILLED",
                Instant.now(),
                "trade-executor",
                1,
                tradeEventPayload
        );

        logger.info(
                "fillOrder() RETURNING ORDER_FILLED event: orderId={} eventId={} eventType={}",
                orderId,
                resultEvent.getEventId(),
                resultEvent.getEventType()
        );

        return resultEvent;
    }

    /**
     * Reject an order.
     *
     * This method has its own transaction.
     *
     * It returns the trade event to the caller.
     * The caller publishes it after this transaction commits.
     */
    @Transactional
    public EventEnvelope<TradeEventPayload> rejectOrder(
            String orderId,
            OrderPlacedPayload payload,
            String reason) {

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

            return null;
        }

        logger.info(
                "Order changed to REJECTED: orderId={} reason={}",
                orderId,
                reason
        );

        TradeEventPayload tradeEventPayload =
                new TradeEventPayload(
                        payload.getOrderId(),
                        payload.getAccountId(),
                        payload.getSymbol(),
                        payload.getSide(),
                        payload.getQuantity(),
                        payload.getPrice(),
                        null,
                        "REJECTED",
                        reason,
                        BigDecimal.ZERO,
                        0,
                        BigDecimal.ZERO,
                        Instant.now()
                );

        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                "ORDER_REJECTED",
                Instant.now(),
                "trade-executor",
                1,
                tradeEventPayload
        );
    }
}