package com.capstone.executor.service;

import com.capstone.executor.client.FauxnanceQuoteClient;
import com.capstone.executor.domain.ExecutionDecision;
import com.capstone.executor.domain.FillRule;
import com.capstone.executor.domain.Quote;
import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.OrderPlacedPayload;
import com.capstone.executor.event.TradeEventPayload;
import com.capstone.executor.mapper.AccountDto;
import com.capstone.executor.mapper.AccountMapper;
import com.capstone.executor.mapper.InstrumentDto;
import com.capstone.executor.mapper.InstrumentMapper;
import com.capstone.executor.mapper.OrderDto;
import com.capstone.executor.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;


@Service
public class OrderExecutionService {

    private static final Logger logger =
            LoggerFactory.getLogger(OrderExecutionService.class);

    private final FauxnanceQuoteClient quoteClient;
    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final SettlementService settlementService;
    private final TradeEventPublisher tradeEventPublisher;

    public OrderExecutionService(
            FauxnanceQuoteClient quoteClient,
            OrderMapper orderMapper,
            AccountMapper accountMapper,
            InstrumentMapper instrumentMapper,
            SettlementService settlementService,
            TradeEventPublisher tradeEventPublisher) {

        this.quoteClient = quoteClient;
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.settlementService = settlementService;
        this.tradeEventPublisher = tradeEventPublisher;
    }


    public void executeOrder(
            EventEnvelope<OrderPlacedPayload> orderEvent) {

        OrderPlacedPayload payload =
                orderEvent.getPayload();

        if (payload == null) {

            throw new IllegalArgumentException(
                    "Order payload is missing"
            );
        }

        String orderId =
                payload.getOrderId();

        if (orderId == null || orderId.isBlank()) {

            throw new IllegalArgumentException(
                    "orderId is missing"
            );
        }

        try {

        
            OrderDto order =
                    orderMapper.findById(orderId);

            if (order == null) {

                logger.warn(
                        "Order not found: orderId={}",
                        orderId
                );

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

           
            AccountDto account =
                    accountMapper.findById(
                            payload.getAccountId()
                    );

            if (account == null) {

                logger.info(
                        "Account not found during execution: "
                                + "orderId={} accountId={}",
                        orderId,
                        payload.getAccountId()
                );

                EventEnvelope<TradeEventPayload> event =
                        settlementService.rejectOrder(
                                orderId,
                                payload,
                                "ACCOUNT_NOT_FOUND"
                        );

                publishTradeEvent(event);

                return;
            }

            if (!account.isActive()) {

                logger.info(
                        "Account not active: orderId={} accountId={}",
                        orderId,
                        payload.getAccountId()
                );

                EventEnvelope<TradeEventPayload> event =
                        settlementService.rejectOrder(
                                orderId,
                                payload,
                                "ACCOUNT_NOT_ACTIVE"
                        );

                publishTradeEvent(event);

                return;
            }

            InstrumentDto instrument =
                    instrumentMapper.findBySymbol(
                            payload.getSymbol()
                    );

            if (instrument == null
                    || !instrument.isTradable()) {

                logger.info(
                        "Instrument not tradable: "
                                + "orderId={} symbol={}",
                        orderId,
                        payload.getSymbol()
                );

                EventEnvelope<TradeEventPayload> event =
                        settlementService.rejectOrder(
                                orderId,
                                payload,
                                "INSTRUMENT_NOT_TRADABLE"
                        );

                publishTradeEvent(event);

                return;
            }

        
            Quote quote =
                    quoteClient.fetchQuote(
                            payload.getSymbol()
                    );

            if (quote == null) {

                logger.warn(
                        "Quote unavailable: "
                                + "orderId={} symbol={}",
                        orderId,
                        payload.getSymbol()
                );

                throw new RuntimeException(
                        "Quote unavailable for symbol: "
                                + payload.getSymbol()
                );
            }

        
            logger.info(
                    "Fetched quote for FillRule decision: orderId={} symbol={} side={} limitPrice={} bid={} ask={} price={}",
                    orderId,
                    payload.getSymbol(),
                    payload.getSide(),
                    payload.getPrice(),
                    quote.getBid(),
                    quote.getAsk(),
                    quote.getPrice()
            );

            ExecutionDecision decision =
                    FillRule.decide(
                            payload.getSide(),
                            payload.getPrice(),
                            quote
                    );

            logger.info(
                    "FillRule decision: orderId={} isFill={} executionPrice={} rejectReason={}",
                    orderId,
                    decision.isFill(),
                    decision.getExecutionPrice(),
                    decision.getRejectReason()
            );

        
            if (decision.isFill()) {

                BigDecimal executionPrice =
                        decision.getExecutionPrice();

                logger.info(
                        "Calling settlementService.fillOrder: orderId={} executionPrice={}",
                        orderId,
                        executionPrice
                );

             
                EventEnvelope<TradeEventPayload> event =
                        settlementService.fillOrder(
                                orderId,
                                payload,
                                executionPrice,
                                instrument
                        );

                logger.info(
                        "Settlement transaction committed: orderId={} eventReturned={} eventType={}",
                        orderId,
                        event != null,
                        event != null ? event.getEventType() : "null"
                );

           
                publishTradeEvent(event);

                logger.info(
                        "Order execution completed: "
                                + "orderId={} status=FILLED",
                        orderId
                );

                return;
            }

         
            String rejectReason =
                    decision.getRejectReason();

            if ("PRICING_UNAVAILABLE"
                    .equals(rejectReason)) {

                logger.warn(
                        "Pricing unavailable: "
                                + "orderId={} symbol={} "
                                + "bid={} ask={} price={}",
                        orderId,
                        payload.getSymbol(),
                        quote.getBid(),
                        quote.getAsk(),
                        quote.getPrice()
                );

                throw new RuntimeException(
                        "Pricing unavailable: bid="
                                + quote.getBid()
                                + " ask="
                                + quote.getAsk()
                                + " price="
                                + quote.getPrice()
                );
            }

        
            EventEnvelope<TradeEventPayload> event =
                    settlementService.rejectOrder(
                            orderId,
                            payload,
                            rejectReason
                    );

            publishTradeEvent(event);

        } catch (Exception e) {

            logger.error(
                    "Execution error (will attempt recovery/rejection): orderId={} exceptionType={} message={} recoverable={}",
                    orderId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    isRecoverableExecutionException(e),
                    e
            );

            if (isRecoverableExecutionException(e)) {
                throw e;
            }

           
            try {

                EventEnvelope<TradeEventPayload> event =
                        settlementService.rejectOrder(
                                orderId,
                                payload,
                                "SYSTEM_ERROR"
                        );

                publishTradeEvent(event);

            } catch (Exception rejectionException) {

                logger.error(
                        "Failed to reject order after "
                                + "execution error: orderId={} "
                                + "error={}",
                        orderId,
                        rejectionException.getMessage(),
                        rejectionException
                );

                throw rejectionException;
            }
        }
    }

 
    private void publishTradeEvent(
            EventEnvelope<TradeEventPayload> event) {

        if (event == null) {

            logger.info(
                    "No trade event to publish because order "
                            + "was already resolved"
            );

            return;
        }

        logger.info(
                "Publishing trade event: orderId={} eventType={}",
                event.getPayload().getOrderId(),
                event.getEventType()
        );

        tradeEventPublisher.publish(event);

        logger.info(
                "Trade event publication completed: "
                        + "orderId={} eventType={}",
                event.getPayload().getOrderId(),
                event.getEventType()
        );
    }

    private boolean isRecoverableExecutionException(
            Exception exception) {

        String message =
                exception.getMessage();

        if (message == null) {
            return false;
        }

        return message.contains("Order not found")
                || message.contains(
                        "Exhausted optimistic lock retries"
                )
                || message.contains("Quote unavailable")
                || message.contains("Pricing unavailable")
                || message.contains("Kafka")
                || message.contains("Trade event publish failed");
    }
}