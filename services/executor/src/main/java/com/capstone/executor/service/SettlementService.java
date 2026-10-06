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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Service
public class SettlementService {

    private static final Logger logger =
            LoggerFactory.getLogger(SettlementService.class);

    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final PortfolioMapper portfolioMapper;
    private final PortfolioHoldingsMapper portfolioHoldingsMapper;
    private final TransactionMapper transactionMapper;
        private final FxConversionService fxConversionService;

    public SettlementService(
            OrderMapper orderMapper,
            AccountMapper accountMapper,
            PortfolioMapper portfolioMapper,
            PortfolioHoldingsMapper portfolioHoldingsMapper,
            TransactionMapper transactionMapper,
            FxConversionService fxConversionService) {

        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.portfolioMapper = portfolioMapper;
        this.portfolioHoldingsMapper = portfolioHoldingsMapper;
        this.transactionMapper = transactionMapper;
        this.fxConversionService = fxConversionService;
    }

   
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

        BigDecimal cashDelta = BigDecimal.ZERO;

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

          
            if (!currentAccount.isActive()) {

                throw new IllegalStateException(
                        "Account became inactive during settlement: orderId="
                                + orderId
                );
            }

            // Deduct the full executed price amount at settlement
            BigDecimal tradeNotional = normalizedPrice.multiply(quantity);
            BigDecimal requiredInAccountCurrency = fxConversionService.convert(
                    tradeNotional,
                    instrument.getQuoteCurrency(),
                    currentAccount.getCurrency()
            );
            BigDecimal cashDeltaForAttempt = "BUY".equalsIgnoreCase(payload.getSide())
                    ? requiredInAccountCurrency.negate()
                    : requiredInAccountCurrency;

            // For BUY orders, verify available balance >= executed price
            if ("BUY".equalsIgnoreCase(payload.getSide())) {
                if (currentAccount.getCashBalance()
                        .compareTo(requiredInAccountCurrency) < 0) {

                    throw new IllegalStateException(
                            "Insufficient funds at settlement: orderId="
                                    + orderId
                                    + " executed price required=" + requiredInAccountCurrency
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

          
            int cashUpdated =
                    accountMapper.updateCashWithVersionGuard(
                            payload.getAccountId(),
                            cashDeltaForAttempt,
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
                    cashDeltaForAttempt
            );

            cashDelta = cashDeltaForAttempt;

            PortfolioDto portfolio =
                    portfolioMapper.findByAccountId(
                            payload.getAccountId()
                    );

            if (portfolio == null) {
                logger.warn(
                        "Portfolio not found for account (creating now): orderId={} accountId={}",
                        orderId,
                        payload.getAccountId()
                );
                portfolioMapper.insert(payload.getAccountId());
                
                portfolio = portfolioMapper.findByAccountId(
                        payload.getAccountId()
                );
                
                if (portfolio == null) {
                    throw new IllegalStateException(
                            "Failed to create portfolio for account: orderId="
                                    + orderId
                    );
                }
            }

        
            PortfolioHoldingsDto holding =
                    portfolioHoldingsMapper
                            .findByPortfolioAndInstrument(
                                    portfolio.getPortfolioId(),
                                    instrument.getInstrumentId()
                            );

           
            if ("BUY".equalsIgnoreCase(payload.getSide())) {

                if (holding == null) {

                    try {
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

                    } catch (DataIntegrityViolationException e) {
                        logger.warn(
                                "Position insert failed (likely duplicate from concurrent order), "
                                        + "refetching: orderId={} symbol={}",
                                orderId,
                                payload.getSymbol(),
                                e
                        );

                        holding = portfolioHoldingsMapper
                                .findByPortfolioAndInstrument(
                                        portfolio.getPortfolioId(),
                                        instrument.getInstrumentId()
                                );

                        if (holding != null) {
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
                                        "Position update failed after concurrent insert: orderId="
                                                + orderId
                                );
                            }

                            positionQuantityAfter = newQuantity;
                            averageCostAfter = newAveragePrice;

                            logger.debug(
                                    "Position updated (after concurrent insert): orderId={} "
                                            + "symbol={} qty={} avgPrice={}",
                                    orderId,
                                    payload.getSymbol(),
                                    newQuantity,
                                    newAveragePrice
                            );
                        } else {
                            throw new IllegalStateException(
                                    "Position not found after concurrent insert: orderId="
                                            + orderId
                            );
                        }
                    }

                } else {

                   
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