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
    private static final Logger logger = LoggerFactory.getLogger(OrderExecutionService.class);
    private final FauxnanceQuoteClient quoteClient;
    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final TradeEventPublisher tradeEventPublisher;
    
    public OrderExecutionService(FauxnanceQuoteClient quoteClient, OrderMapper orderMapper, AccountMapper accountMapper, InstrumentMapper instrumentMapper, TradeEventPublisher tradeEventPublisher) {
        this.quoteClient = quoteClient;
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
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
                logger.warn("Order not found: orderId={}", orderId);
                return;
            }
            if (!"NEW".equals(order.getStatus())) {
                logger.info("Order already resolved: orderId={} status={}", orderId, order.getStatus());
                return;
            }
            
            // Re-check account ACTIVE status (Sprint 5 rule 2)
            AccountDto account = accountMapper.findById(payload.getAccountId());
            if (account == null || !account.isActive()) {
                logger.info("Account not active: orderId={} accountId={}", orderId, payload.getAccountId());
                rejectOrder(orderId, payload, "ACCOUNT_NOT_ACTIVE");
                return;
            }
            
            // Re-check instrument tradable (Sprint 5 rule 3)
            InstrumentDto instrument = instrumentMapper.findBySymbol(payload.getSymbol());
            if (instrument == null || !instrument.isTradable()) {
                logger.info("Instrument not tradable: orderId={} symbol={}", orderId, payload.getSymbol());
                rejectOrder(orderId, payload, "INSTRUMENT_NOT_TRADABLE");
                return;
            }
            
            // Fetch live quote
            Quote quote = quoteClient.fetchQuote(payload.getSymbol());
            
            // Apply pure fill rule
            ExecutionDecision decision = FillRule.decide(payload.getSide(), payload.getPrice(), quote);
            
            if (decision.isFill()) {
                // Re-check execution-time funds for BUY (Sprint 5 rule 6)
                if ("BUY".equalsIgnoreCase(payload.getSide())) {
                    BigDecimal totalCost = decision.getExecutionPrice().multiply(BigDecimal.valueOf(payload.getQuantity()));
                    if (account.getCashBalance().compareTo(totalCost) < 0) {
                        logger.info("Insufficient funds at execution: orderId={} required={} available={}", orderId, totalCost, account.getCashBalance());
                        rejectOrder(orderId, payload, "INSUFFICIENT_FUNDS");
                        return;
                    }
                }
                
                // Fill the order transactionally
                fillOrder(orderId, payload, decision.getExecutionPrice(), account, instrument);
            } else {
                // Reject: order cannot be left at NEW forever
                rejectOrder(orderId, payload, decision.getRejectReason());
            }
            
        } catch (Exception e) {
            logger.error("Execution error: orderId={} error={}", orderId, e.getMessage(), e);
            try {
                rejectOrder(orderId, payload, "SYSTEM_ERROR");
            } catch (Exception ignored) {}
        }
    }
    
    // Fill order transactionally: update DB then publish event
    @Transactional
    private void fillOrder(String orderId, OrderPlacedPayload payload, BigDecimal executionPrice, AccountDto account, InstrumentDto instrument) {
        BigDecimal normalizedPrice = PriceNormalizer.normalize(executionPrice);
        Instant now = Instant.now();
        
        // Guarded update: returns 1 if successful, 0 if not NEW (idempotent)
        int updated = orderMapper.updateToFilled(orderId, normalizedPrice, now);
        if (updated == 0) {
            logger.info("Order already resolved (idempotent): orderId={}", orderId);
            return;
        }
        
        // Calculate position and cash delta (stub - actual implementation depends on portfolio service)
        BigDecimal cashDelta = normalizedPrice.multiply(BigDecimal.valueOf(payload.getQuantity()));
        if ("BUY".equalsIgnoreCase(payload.getSide())) {
            cashDelta = cashDelta.negate();
        }
        
        // Publish trade event after DB commit but before offset commit
        publishTradeEvent(payload, "ORDER_FILLED", normalizedPrice, null, cashDelta, 0, BigDecimal.ZERO, now);
        logger.info("Order filled: orderId={} price={}", orderId, normalizedPrice);
    }
    
    // Reject order transactionally: update DB then publish event
    @Transactional
    private void rejectOrder(String orderId, OrderPlacedPayload payload, String reason) {
        // Guarded update: returns 1 if successful, 0 if not NEW (idempotent)
        int updated = orderMapper.updateToRejected(orderId, reason);
        if (updated == 0) {
            logger.info("Order already resolved (idempotent): orderId={}", orderId);
            return;
        }
        
        // Publish rejection event after DB commit
        publishTradeEvent(payload, "ORDER_REJECTED", null, reason, BigDecimal.ZERO, 0, BigDecimal.ZERO, Instant.now());
        logger.info("Order rejected: orderId={} reason={}", orderId, reason);
    }
    
    // Publish event to trade-events topic keyed by accountId
    private void publishTradeEvent(OrderPlacedPayload payload, String eventType, BigDecimal executedPrice, String reason, BigDecimal cashDelta, Integer positionQuantity, BigDecimal avgCost, Instant executedOn) {
        TradeEventPayload tradeEvent = new TradeEventPayload(
            payload.getOrderId(), payload.getAccountId(), payload.getSymbol(), payload.getSide(), 
            payload.getQuantity(), payload.getPrice(), executedPrice,
            eventType.equals("ORDER_FILLED") ? "FILLED" : "REJECTED",
            reason, cashDelta, positionQuantity, avgCost, executedOn
        );
        
        EventEnvelope<TradeEventPayload> event = new EventEnvelope<>(
            UUID.randomUUID().toString(), eventType, Instant.now(), "trade-executor", 1, tradeEvent
        );
        
        tradeEventPublisher.publish(event);
    }
}
