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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Unit tests for OrderExecutionService: all 6 execution paths
@ExtendWith(MockitoExtension.class)
public class OrderExecutionServiceTest {
    
    @Mock private FauxnanceQuoteClient quoteClient;
    @Mock private OrderMapper orderMapper;
    @Mock private AccountMapper accountMapper;
    @Mock private InstrumentMapper instrumentMapper;
    @Mock private TradeEventPublisher tradeEventPublisher;
    private OrderExecutionService service;
    private List<EventEnvelope<TradeEventPayload>> publishedEvents;
    
    @BeforeEach
    public void setup() {
        publishedEvents = new ArrayList<>();
        lenient().doAnswer(inv -> { publishedEvents.add(inv.getArgument(0)); return null; })
            .when(tradeEventPublisher).publish(any());
        service = new OrderExecutionService(quoteClient, orderMapper, accountMapper, instrumentMapper, tradeEventPublisher);
    }
    
    // Test 1: BUY limit at or above quote fills at quote
    @Test
    public void testBuyLimitAtOrAboveQuoteFills() {
        OrderDto order = new OrderDto("order-1", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("233.00"), "NEW", "key1", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); account.setAccountId(1L); account.setStatus("ACTIVE"); account.setCashBalance(new BigDecimal("100000"));
        InstrumentDto instrument = new InstrumentDto(); instrument.setSymbol("AAPL"); instrument.setTradable(true);
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        
        when(orderMapper.findById("order-1")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);
        when(orderMapper.updateToFilled(anyString(), any(), any())).thenReturn(1);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-1", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-1", 1L, "AAPL", "BUY", 100, new BigDecimal("233.00"), "key1", Instant.now()));
        
        service.executeOrder(event);
        
        assertEquals(1, publishedEvents.size());
        TradeEventPayload payload = publishedEvents.get(0).getPayload();
        assertEquals("FILLED", payload.getStatus());
        assertEquals("ORDER_FILLED", publishedEvents.get(0).getEventType());
    }
    
    // Test 2: SELL limit at or below quote fills at quote
    @Test
    public void testSellLimitAtOrBelowQuoteFills() {
        OrderDto order = new OrderDto("order-2", 1L, "acc-1", 1L, "SELL", 100, new BigDecimal("232.00"), "NEW", "key2", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); account.setAccountId(1L); account.setStatus("ACTIVE"); account.setCashBalance(new BigDecimal("100000"));
        InstrumentDto instrument = new InstrumentDto(); instrument.setSymbol("AAPL"); instrument.setTradable(true);
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        
        when(orderMapper.findById("order-2")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);
        when(orderMapper.updateToFilled(anyString(), any(), any())).thenReturn(1);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-2", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-2", 1L, "AAPL", "SELL", 100, new BigDecimal("232.00"), "key2", Instant.now()));
        
        service.executeOrder(event);
        
        assertEquals(1, publishedEvents.size());
        assertEquals("FILLED", publishedEvents.get(0).getPayload().getStatus());
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
        assertEquals("REJECTED", publishedEvents.get(0).getPayload().getStatus());
        assertEquals("PRICE_NOT_MET", publishedEvents.get(0).getPayload().getReason());
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
        assertEquals("REJECTED", publishedEvents.get(0).getPayload().getStatus());
        assertEquals("INSUFFICIENT_FUNDS", publishedEvents.get(0).getPayload().getReason());
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
        assertEquals("REJECTED", publishedEvents.get(0).getPayload().getStatus());
        assertEquals("ACCOUNT_NOT_ACTIVE", publishedEvents.get(0).getPayload().getReason());
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
        assertEquals("REJECTED", publishedEvents.get(0).getPayload().getStatus());
        assertEquals("PRICING_UNAVAILABLE", publishedEvents.get(0).getPayload().getReason());
        // Verify order was updated, not left at NEW
        verify(orderMapper).updateToRejected("order-6", "PRICING_UNAVAILABLE");
    }
    
    // Test: Idempotent duplicate delivery - second delivery is no-op
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
}
