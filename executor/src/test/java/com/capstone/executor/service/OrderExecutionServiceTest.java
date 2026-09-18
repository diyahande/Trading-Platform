package com.capstone.executor.service;

import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.OrderPlacedPayload;
import com.capstone.executor.event.TradeEventPayload;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderExecutionService.
 * 
 * Covers 6 execution paths from Sprint 5 and 4 Sprint 10 acceptance criteria:
 * 1-3: Basic execution (BUY fill, SELL fill, rejection)
 * 4-6: Failure paths (insufficient funds, suspended account, no price)
 * 7: Idempotent duplicate delivery
 * 8-11: Sprint 10 transactional settlement with optimistic lock
 */
@ExtendWith(MockitoExtension.class)
public class OrderExecutionServiceTest {
    
    @Mock private FauxnanceQuoteClient quoteClient;
    @Mock private OrderMapper orderMapper;
    @Mock private AccountMapper accountMapper;
    @Mock private InstrumentMapper instrumentMapper;
    @Mock private PortfolioMapper portfolioMapper;
    @Mock private PortfolioHoldingsMapper portfolioHoldingsMapper;
    @Mock private TransactionMapper transactionMapper;
    @Mock private TradeEventPublisher tradeEventPublisher;
    private OrderExecutionService service;
    private List<EventEnvelope<?>> publishedEvents;
    
    @BeforeEach
    public void setup() {
        publishedEvents = new ArrayList<>();
        lenient().doAnswer(inv -> { publishedEvents.add(inv.getArgument(0)); return null; })
            .when(tradeEventPublisher).publish(any());
        lenient().doNothing().when(transactionMapper).insert(anyString(), anyLong(), any(BigDecimal.class), any(BigDecimal.class), anyString());
        service = new OrderExecutionService(quoteClient, orderMapper, accountMapper, instrumentMapper, portfolioMapper, portfolioHoldingsMapper, transactionMapper, tradeEventPublisher);
    }
    
    // Test 1: BUY limit at or above quote fills at quote
    @Test
    public void testBuyLimitAtOrAboveQuoteFills() {
        OrderDto order = new OrderDto("order-1", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("233.00"), "NEW", "key1", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); account.setAccountId(1L); account.setStatus("ACTIVE"); account.setCashBalance(new BigDecimal("100000")); account.setVersion(0L);
        InstrumentDto instrument = new InstrumentDto(); instrument.setSymbol("AAPL"); instrument.setTradable(true); instrument.setInstrumentId(1L);
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        PortfolioDto portfolio = new PortfolioDto(); portfolio.setPortfolioId(100L); portfolio.setAccountId(1L);
        
        when(orderMapper.findById("order-1")).thenReturn(order);
        lenient().when(accountMapper.findById(anyLong())).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);
        when(orderMapper.updateToFilled(eq("order-1"), any(BigDecimal.class), any(Instant.class))).thenReturn(1);
        when(accountMapper.updateCashWithVersionGuard(eq(1L), any(BigDecimal.class), any(Long.class))).thenReturn(1);
        when(portfolioMapper.findByAccountId(eq(1L))).thenReturn(portfolio);
        when(portfolioHoldingsMapper.findByPortfolioAndInstrument(eq(100L), eq(1L))).thenReturn(null);
        lenient().doNothing().when(portfolioHoldingsMapper).insert(anyLong(), anyLong(), any(BigDecimal.class), any(BigDecimal.class));
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-1", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-1", 1L, "AAPL", "BUY", 100, new BigDecimal("233.00"), "key1", Instant.now()));
        
        service.executeOrder(event);
        
        assertEquals(1, publishedEvents.size());
        assertEquals("ORDER_FILLED", publishedEvents.get(0).getEventType());
    }
    
    // Test 2: SELL limit at or below quote fills at quote
    @Test
    public void testSellLimitAtOrBelowQuoteFills() {
        OrderDto order = new OrderDto("order-2", 1L, "acc-1", 1L, "SELL", 100, new BigDecimal("232.00"), "NEW", "key2", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); account.setAccountId(1L); account.setStatus("ACTIVE"); account.setCashBalance(new BigDecimal("100000")); account.setVersion(0L);
        InstrumentDto instrument = new InstrumentDto(); instrument.setSymbol("AAPL"); instrument.setTradable(true); instrument.setInstrumentId(1L);
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        PortfolioDto portfolio = new PortfolioDto(); portfolio.setPortfolioId(100L);
        PortfolioHoldingsDto holding = new PortfolioHoldingsDto(); holding.setQuantity(new BigDecimal("200")); holding.setAveragePrice(new BigDecimal("200")); holding.setVersion(0L);
        
        when(orderMapper.findById("order-2")).thenReturn(order);
        lenient().when(accountMapper.findById(anyLong())).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);
        when(orderMapper.updateToFilled(eq("order-2"), any(BigDecimal.class), any(Instant.class))).thenReturn(1);
        when(accountMapper.updateCashWithVersionGuard(eq(1L), any(BigDecimal.class), any(Long.class))).thenReturn(1);
        when(portfolioMapper.findByAccountId(eq(1L))).thenReturn(portfolio);
        when(portfolioHoldingsMapper.findByPortfolioAndInstrument(eq(100L), eq(1L))).thenReturn(holding);
        when(portfolioHoldingsMapper.update(eq(100L), eq(1L), any(BigDecimal.class), any(BigDecimal.class))).thenReturn(1);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-2", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-2", 1L, "AAPL", "SELL", 100, new BigDecimal("232.00"), "key2", Instant.now()));
        
        service.executeOrder(event);
        
        assertEquals(1, publishedEvents.size());
        assertEquals("ORDER_FILLED", publishedEvents.get(0).getEventType());
    }
    
    // Test 3: Order outside marketable range rejected
    @Test
    public void testOrderOutsideMarketableRangeRejected() {
        OrderDto order = new OrderDto("order-3", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("232.00"), "NEW", "key3", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); account.setAccountId(1L); account.setStatus("ACTIVE"); account.setCashBalance(new BigDecimal("100000"));
        InstrumentDto instrument = new InstrumentDto(); instrument.setSymbol("AAPL"); instrument.setTradable(true);
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        
        when(orderMapper.findById("order-3")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);
        when(orderMapper.updateToRejected(anyString(), anyString())).thenReturn(1);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-3", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-3", 1L, "AAPL", "BUY", 100, new BigDecimal("232.00"), "key3", Instant.now()));
        
        service.executeOrder(event);
        
        assertEquals(1, publishedEvents.size());
        assertEquals("ORDER_REJECTED", publishedEvents.get(0).getEventType());
    }
    
    // Test 4: Execution-time insufficient funds rejection
    @Test
    public void testExecutionTimeInsufficientFundsRejected() {
        OrderDto order = new OrderDto("order-4", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("233.00"), "NEW", "key4", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); account.setAccountId(1L); account.setStatus("ACTIVE"); account.setCashBalance(new BigDecimal("20000"));
        InstrumentDto instrument = new InstrumentDto(); instrument.setSymbol("AAPL"); instrument.setTradable(true);
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        
        when(orderMapper.findById("order-4")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);
        when(orderMapper.updateToRejected(anyString(), anyString())).thenReturn(1);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-4", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-4", 1L, "AAPL", "BUY", 100, new BigDecimal("233.00"), "key4", Instant.now()));
        
        service.executeOrder(event);
        
        assertEquals(1, publishedEvents.size());
        assertEquals("ORDER_REJECTED", publishedEvents.get(0).getEventType());
    }
    
    // Test 5: Suspended account does not trade
    @Test
    public void testSuspendedAccountDoesNotTrade() {
        OrderDto order = new OrderDto("order-5", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("233.00"), "NEW", "key5", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); account.setAccountId(1L); account.setStatus("SUSPENDED"); account.setCashBalance(new BigDecimal("100000"));
        
        when(orderMapper.findById("order-5")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(orderMapper.updateToRejected(anyString(), anyString())).thenReturn(1);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-5", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-5", 1L, "AAPL", "BUY", 100, new BigDecimal("233.00"), "key5", Instant.now()));
        
        service.executeOrder(event);
        
        assertEquals(1, publishedEvents.size());
        assertEquals("ORDER_REJECTED", publishedEvents.get(0).getEventType());
        verify(quoteClient, never()).fetchQuote(any());
    }
    
    // Test 6: No price available: order resolved not left at NEW
    @Test
    public void testNoPriceAvailableResolved() {
        OrderDto order = new OrderDto("order-6", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("233.00"), "NEW", "key6", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); account.setAccountId(1L); account.setStatus("ACTIVE"); account.setCashBalance(new BigDecimal("100000"));
        InstrumentDto instrument = new InstrumentDto(); instrument.setSymbol("AAPL"); instrument.setTradable(true);
        
        when(orderMapper.findById("order-6")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(null);
        when(orderMapper.updateToRejected(anyString(), anyString())).thenReturn(1);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-6", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-6", 1L, "AAPL", "BUY", 100, new BigDecimal("233.00"), "key6", Instant.now()));
        
        service.executeOrder(event);
        
        assertEquals(1, publishedEvents.size());
        assertEquals("ORDER_REJECTED", publishedEvents.get(0).getEventType());
        verify(orderMapper).updateToRejected("order-6", "PRICING_UNAVAILABLE");
    }
    
    // Test 7: Idempotent duplicate delivery - second delivery is no-op
    @Test
    public void testIdempotentDuplicateDelivery() {
        OrderDto order = new OrderDto("order-dup", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("233.00"), "FILLED", "key-dup", Instant.now(), new BigDecimal("232.60"), Instant.now(), null);
        
        when(orderMapper.findById("order-dup")).thenReturn(order);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-dup", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-dup", 1L, "AAPL", "BUY", 100, new BigDecimal("233.00"), "key-dup", Instant.now()));
        
        service.executeOrder(event);
        
        // No event published for already-resolved order
        assertEquals(0, publishedEvents.size());
        verify(accountMapper, never()).findById(any());
    }
    
    /**
     * SPRINT 10 TEST 1: Settled order writes status, cash and position together.
     * All three writes succeed atomically and event is published.
     */
    @Test
    public void testSettledOrderWritesStatusCashPositionTogether() {
        String orderId = UUID.randomUUID().toString();
        EventEnvelope<OrderPlacedPayload> event = createOrderEvent(orderId, 1L, 50, 151.00);

        OrderDto order = new OrderDto();
        order.setOrderId(orderId);
        order.setStatus("NEW");
        when(orderMapper.findById(orderId)).thenReturn(order);

        AccountDto account = createAccount(1L, BigDecimal.valueOf(10000), 0L);
        lenient().when(accountMapper.findById(anyLong())).thenReturn(account);

        InstrumentDto instrument = new InstrumentDto();
        instrument.setInstrumentId(100L);
        instrument.setSymbol("AAPL");
        instrument.setTradable(true);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);

        // Quote: bid=149, ask=151. BUY limit at 151+ fills at ask=151
        Quote quote = new Quote("AAPL", BigDecimal.valueOf(150), BigDecimal.valueOf(149),
                BigDecimal.valueOf(151), "USD", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "REGULAR", false, Instant.now());
        when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);

        when(orderMapper.updateToFilled(eq(orderId), any(BigDecimal.class), any(Instant.class))).thenReturn(1);
        when(accountMapper.updateCashWithVersionGuard(eq(1L), any(BigDecimal.class), any(Long.class))).thenReturn(1);

        PortfolioDto portfolio = new PortfolioDto();
        portfolio.setPortfolioId(200L);
        lenient().when(portfolioMapper.findByAccountId(anyLong())).thenReturn(portfolio);
        when(portfolioHoldingsMapper.findByPortfolioAndInstrument(eq(200L), eq(100L))).thenReturn(null);
        lenient().doNothing().when(portfolioHoldingsMapper).insert(eq(200L), eq(100L), any(BigDecimal.class), any(BigDecimal.class));

        service.executeOrder(event);

        verify(orderMapper, times(1)).updateToFilled(eq(orderId), any(BigDecimal.class), any(Instant.class));
        verify(accountMapper, times(1)).updateCashWithVersionGuard(eq(1L), any(BigDecimal.class), eq(0L));
        verify(portfolioHoldingsMapper, times(1)).insert(eq(200L), eq(100L), any(BigDecimal.class), any(BigDecimal.class));
        assertEquals(1, publishedEvents.size());
        assertEquals("ORDER_FILLED", publishedEvents.get(0).getEventType());
    }
    
    /**
     * SPRINT 10 TEST 2: Optimistic lock retry on version conflict.
     * Account version doesn't match on first attempt, retries and succeeds.
     */
    @Test
    public void testOptimisticLockRetryOnVersionConflict() {
        String orderId = UUID.randomUUID().toString();
        EventEnvelope<OrderPlacedPayload> event = createOrderEvent(orderId, 1L, 50, 151.00);

        OrderDto order = new OrderDto();
        order.setOrderId(orderId);
        order.setStatus("NEW");
        when(orderMapper.findById(orderId)).thenReturn(order);

        AccountDto account = createAccount(1L, BigDecimal.valueOf(10000), 0L);
        lenient().when(accountMapper.findById(anyLong())).thenReturn(account);

        InstrumentDto instrument = new InstrumentDto();
        instrument.setInstrumentId(100L);
        instrument.setSymbol("AAPL");
        instrument.setTradable(true);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);

        Quote quote = new Quote("AAPL", BigDecimal.valueOf(150), BigDecimal.valueOf(149),
                BigDecimal.valueOf(151), "USD", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "REGULAR", false, Instant.now());
        when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);

        when(orderMapper.updateToFilled(eq(orderId), any(BigDecimal.class), any(Instant.class))).thenReturn(1);
        when(accountMapper.updateCashWithVersionGuard(eq(1L), any(BigDecimal.class), any(Long.class)))
                .thenReturn(0)  // First attempt fails
                .thenReturn(1); // Retry succeeds

        PortfolioDto portfolio = new PortfolioDto();
        portfolio.setPortfolioId(200L);
        lenient().when(portfolioMapper.findByAccountId(anyLong())).thenReturn(portfolio);
        when(portfolioHoldingsMapper.findByPortfolioAndInstrument(eq(200L), eq(100L))).thenReturn(null);
        lenient().doNothing().when(portfolioHoldingsMapper).insert(anyLong(), anyLong(), any(BigDecimal.class), any(BigDecimal.class));

        service.executeOrder(event);

        verify(tradeEventPublisher, times(1)).publish(any(EventEnvelope.class));
        verify(accountMapper, times(2)).updateCashWithVersionGuard(eq(1L), any(BigDecimal.class), anyLong());
    }
    
    /**
     * SPRINT 10 TEST 3: Second delivery (idempotent) - zero rows affected on status update.
     * Order already filled, so updateToFilled returns 0, no cash/position updates, no event.
     */
    @Test
    public void testSecondDeliveryAffectsZeroRowsPublishesNothing() {
        String orderId = UUID.randomUUID().toString();
        EventEnvelope<OrderPlacedPayload> event =
                createOrderEvent(orderId, 1L, 50, 151.00);

        OrderDto order = new OrderDto();
        order.setOrderId(orderId);
        order.setStatus("NEW");

        AccountDto account = createAccount(1L, BigDecimal.valueOf(10000), 0L);

        InstrumentDto instrument = new InstrumentDto();
        instrument.setInstrumentId(100L);
        instrument.setSymbol("AAPL");
        instrument.setTradable(true);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);

        Quote quote = new Quote("AAPL", BigDecimal.valueOf(150), BigDecimal.valueOf(149),
                BigDecimal.valueOf(151), "USD", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "REGULAR", false, Instant.now());
        when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);

        when(orderMapper.findById(orderId)).thenReturn(order);
        lenient().when(accountMapper.findById(anyLong())).thenReturn(account);
        when(orderMapper.updateToFilled(eq(orderId), any(BigDecimal.class), any(Instant.class))).thenReturn(0);

        service.executeOrder(event);

        verify(orderMapper).updateToFilled(eq(orderId), any(BigDecimal.class), any(Instant.class));

        verify(accountMapper, never())
                .updateCashWithVersionGuard(anyLong(), any(BigDecimal.class), anyLong());

        verify(portfolioHoldingsMapper, never())
                .insert(anyLong(), anyLong(), any(BigDecimal.class), any(BigDecimal.class));

        verify(tradeEventPublisher, never()).publish(any());
    }
    
    /**
     * SPRINT 10 TEST 4: Exhausted optimistic-lock budget reported as error.
     * Account version conflicts on all 3 retry attempts, error thrown.
     */
    @Test
    public void testExhaustedOptimisticLockBudgetReportedAsError() {
        String orderId = UUID.randomUUID().toString();
        EventEnvelope<OrderPlacedPayload> event = createOrderEvent(orderId, 1L, 50, 151.00);

        OrderDto order = new OrderDto();
        order.setOrderId(orderId);
        order.setStatus("NEW");
        lenient().when(orderMapper.findById(orderId)).thenReturn(order);

        AccountDto account = createAccount(1L, BigDecimal.valueOf(10000), 0L);
        lenient().when(accountMapper.findById(anyLong())).thenReturn(account);

        InstrumentDto instrument = new InstrumentDto();
        instrument.setInstrumentId(100L);
        instrument.setSymbol("AAPL");
        instrument.setTradable(true);
        lenient().when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);

        Quote quote = new Quote("AAPL", BigDecimal.valueOf(150), BigDecimal.valueOf(149),
                BigDecimal.valueOf(151), "USD", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "REGULAR", false, Instant.now());
        lenient().when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);

        PortfolioDto portfolio = new PortfolioDto();
        portfolio.setPortfolioId(200L);
        lenient().when(portfolioMapper.findByAccountId(anyLong())).thenReturn(portfolio);
        lenient().when(portfolioHoldingsMapper.findByPortfolioAndInstrument(eq(200L), eq(100L))).thenReturn(null);

        lenient().when(orderMapper.updateToFilled(eq(orderId), any(BigDecimal.class), any(Instant.class))).thenReturn(1);
        // Mock updateCashWithVersionGuard to always return 0 (conflict) to exhaust retries
        when(accountMapper.updateCashWithVersionGuard(eq(1L), any(BigDecimal.class), anyLong())).thenReturn(0);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.executeOrder(event));

        assertTrue(exception.getMessage().contains("Exhausted optimistic lock retries"),
                "Expected error about exhausted retries, got: " + exception.getMessage());

        verify(tradeEventPublisher, never()).publish(any());
    }

    // Helper methods
    private EventEnvelope<OrderPlacedPayload> createOrderEvent(String orderId, Long accountId, int quantity, double price) {
        OrderPlacedPayload payload = new OrderPlacedPayload();
        payload.setOrderId(orderId);
        payload.setAccountId(accountId);
        payload.setSymbol("AAPL");
        payload.setSide("BUY");
        payload.setQuantity(quantity);
        payload.setPrice(BigDecimal.valueOf(price));
        payload.setIdempotencyKey(UUID.randomUUID().toString());

        return new EventEnvelope<>(UUID.randomUUID().toString(), "ORDER_PLACED", Instant.now(), "trade-api", 1, payload);
    }

    private AccountDto createAccount(Long accountId, BigDecimal cashBalance, Long version) {
        AccountDto account = new AccountDto();
        account.setAccountId(accountId);
        account.setUserId(1000L);
        account.setAccountReference(UUID.randomUUID().toString());
        account.setHolderName("Test User");
        account.setCurrency("USD");
        account.setCashBalance(cashBalance);
        account.setStatus("ACTIVE");
        account.setVersion(version);
        account.setCreatedAt(Instant.now());
        return account;
    }
}
