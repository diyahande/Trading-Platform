package com.capstone.executor.service;

import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.OrderPlacedPayload;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class OrderExecutionServiceTest {
    
    @Mock private FauxnanceQuoteClient quoteClient;
    @Mock private OrderMapper orderMapper;
    @Mock private AccountMapper accountMapper;
    @Mock private InstrumentMapper instrumentMapper;
    @Mock private SettlementService settlementService;
    @Mock private TradeEventPublisher tradeEventPublisher;
    
    private OrderExecutionService service;
    
    @BeforeEach
    public void setup() {
        service = new OrderExecutionService(
            quoteClient,
            orderMapper,
            accountMapper,
            instrumentMapper,
            settlementService,
            tradeEventPublisher
        );
    }
    

    @Test
    public void testOrderNotFoundThrows() {
        when(orderMapper.findById("unknown")).thenReturn(null);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>(
            "evt-1", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("unknown", 1L, "AAPL", "BUY", 100, 
                new BigDecimal("233.00"), "key1", Instant.now())
        );
        
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.executeOrder(event)
        );
        assertTrue(ex.getMessage().contains("Order not found"));
    }
    
  
    @Test
    public void testAlreadyResolvedOrderIsNoOp() {
        OrderDto order = new OrderDto();
        order.setOrderId("order-1");
        order.setStatus("FILLED");
        
        when(orderMapper.findById("order-1")).thenReturn(order);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>(
            "evt-1", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-1", 1L, "AAPL", "BUY", 100, 
                new BigDecimal("233.00"), "key1", Instant.now())
        );
        
        service.executeOrder(event);
        
        // No settlement called
        verify(settlementService, never()).fillOrder(any(), any(), any(), any());
        verify(settlementService, never()).rejectOrder(any(), any(), any());
    }

    @Test
    public void testAccountNotFoundRejects() {
        OrderDto order = new OrderDto();
        order.setOrderId("order-2");
        order.setStatus("NEW");
        
        when(orderMapper.findById("order-2")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(null);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>(
            "evt-2", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-2", 1L, "AAPL", "BUY", 100, 
                new BigDecimal("233.00"), "key2", Instant.now())
        );
        
        service.executeOrder(event);
        
        verify(settlementService).rejectOrder(
            "order-2",
            event.getPayload(),
            "ACCOUNT_NOT_FOUND"
        );
    }
    

    @Test
    public void testSuspendedAccountRejects() {
        OrderDto order = new OrderDto();
        order.setOrderId("order-3");
        order.setStatus("NEW");
        
        AccountDto account = new AccountDto();
        account.setAccountId(1L);
        account.setStatus("SUSPENDED");  // Not active
        
        when(orderMapper.findById("order-3")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>(
            "evt-3", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-3", 1L, "AAPL", "BUY", 100, 
                new BigDecimal("233.00"), "key3", Instant.now())
        );
        
        service.executeOrder(event);
        
        verify(settlementService).rejectOrder(
            "order-3",
            event.getPayload(),
            "ACCOUNT_NOT_ACTIVE"
        );
    }
    
   
    @Test
public void testInstrumentNotFoundRejects() {
    OrderDto order = new OrderDto();
    order.setOrderId("order-4");
    order.setStatus("NEW");

    AccountDto account = new AccountDto();
    account.setAccountId(1L);
    account.setStatus("ACTIVE");

    when(orderMapper.findById("order-4")).thenReturn(order);
    when(accountMapper.findById(1L)).thenReturn(account);
    when(instrumentMapper.findBySymbol("UNKNOWN")).thenReturn(null);

    EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>(
        "evt-4",
        "ORDER_PLACED",
        Instant.now(),
        "trade-api",
        1,
        new OrderPlacedPayload(
            "order-4",
            1L,
            "UNKNOWN",
            "BUY",
            100,
            new BigDecimal("233.00"),
            "key4",
            Instant.now()
        )
    );

    service.executeOrder(event);

    verify(settlementService).rejectOrder(
        "order-4",
        event.getPayload(),
        "INSTRUMENT_NOT_TRADABLE"
    );
}
  
    @Test
    public void testInstrumentNotTradableRejects() {
        OrderDto order = new OrderDto();
        order.setOrderId("order-5");
        order.setStatus("NEW");
        
        AccountDto account = new AccountDto();
        account.setAccountId(1L);
        account.setStatus("ACTIVE");
        
        InstrumentDto instrument = new InstrumentDto();
        instrument.setSymbol("AAPL");
        instrument.setTradable(false);  // Suspended
        
        when(orderMapper.findById("order-5")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>(
            "evt-5", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-5", 1L, "AAPL", "BUY", 100, 
                new BigDecimal("233.00"), "key5", Instant.now())
        );
        
        service.executeOrder(event);
        
        verify(settlementService).rejectOrder(
            "order-5",
            event.getPayload(),
            "INSTRUMENT_NOT_TRADABLE"
        );
    }
    

    @Test
    public void testQuoteUnavailableThrows() {
        OrderDto order = new OrderDto();
        order.setOrderId("order-6");
        order.setStatus("NEW");
        
        AccountDto account = new AccountDto();
        account.setAccountId(1L);
        account.setStatus("ACTIVE");
        
        InstrumentDto instrument = new InstrumentDto();
        instrument.setSymbol("AAPL");
        instrument.setTradable(true);
        
        when(orderMapper.findById("order-6")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(null);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>(
            "evt-6", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-6", 1L, "AAPL", "BUY", 100, 
                new BigDecimal("233.00"), "key6", Instant.now())
        );
        
        RuntimeException ex = assertThrows(RuntimeException.class, 
            () -> service.executeOrder(event));
        assertTrue(ex.getMessage().contains("Quote unavailable"));
        
        // Not rejected - will be retried
        verify(settlementService, never()).rejectOrder(any(), any(), any());
    }
  
    @Test
public void testBuyLimitAboveAskFills() {
    OrderDto order = new OrderDto();
    order.setOrderId("order-7");
    order.setStatus("NEW");

    AccountDto account = new AccountDto();
    account.setAccountId(1L);
    account.setStatus("ACTIVE");

    InstrumentDto instrument = new InstrumentDto();
    instrument.setInstrumentId(1L);
    instrument.setSymbol("AAPL");
    instrument.setTradable(true);

    // Quote: bid=149, ask=151
    Quote quote = new Quote(
        "AAPL",
        new BigDecimal("150"),
        new BigDecimal("149"),
        new BigDecimal("151"),
        "USD",
        null,
        null,
        null,
        "open",
        false,
        Instant.now()
    );

    when(orderMapper.findById("order-7")).thenReturn(order);
    when(accountMapper.findById(1L)).thenReturn(account);
    when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
    when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);

    EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>(
        "evt-7",
        "ORDER_PLACED",
        Instant.now(),
        "trade-api",
        1,
        new OrderPlacedPayload(
            "order-7",
            1L,
            "AAPL",
            "BUY",
            100,
            new BigDecimal("151.00"),
            "key7",
            Instant.now()
        )
    );

    service.executeOrder(event);

    // Should delegate to settlement
    verify(settlementService).fillOrder(
        eq("order-7"),
        eq(event.getPayload()),
        argThat(price -> price.compareTo(new BigDecimal("151.00")) == 0),
        eq(instrument)
    );
}
 
    @Test
    public void testBuyLimitBelowAskRejects() {
        OrderDto order = new OrderDto();
        order.setOrderId("order-8");
        order.setStatus("NEW");
        
        AccountDto account = new AccountDto();
        account.setAccountId(1L);
        account.setStatus("ACTIVE");
        
        InstrumentDto instrument = new InstrumentDto();
        instrument.setSymbol("AAPL");
        instrument.setTradable(true);
        
        // Quote: ask=151
        Quote quote = new Quote("AAPL", new BigDecimal("150"), 
            new BigDecimal("149"), new BigDecimal("151"), 
            "USD", null, null, null, "open", false, Instant.now());
        
        when(orderMapper.findById("order-8")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>(
            "evt-8", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-8", 1L, "AAPL", "BUY", 100, 
                new BigDecimal("150.00"), "key8", Instant.now())
        );
        
        service.executeOrder(event);
        
        verify(settlementService).rejectOrder(
            "order-8",
            event.getPayload(),
            "PRICE_NOT_MET"
        );
    }
   
    @Test
public void testSellLimitBelowBidFills() {
    OrderDto order = new OrderDto();
    order.setOrderId("order-9");
    order.setStatus("NEW");

    AccountDto account = new AccountDto();
    account.setAccountId(1L);
    account.setStatus("ACTIVE");

    InstrumentDto instrument = new InstrumentDto();
    instrument.setInstrumentId(1L);
    instrument.setSymbol("AAPL");
    instrument.setTradable(true);

    // Quote: bid=149, ask=151
    Quote quote = new Quote("AAPL", new BigDecimal("150"),
        new BigDecimal("149"), new BigDecimal("151"),
        "USD", null, null, null, "open", false, Instant.now());

    when(orderMapper.findById("order-9")).thenReturn(order);
    when(accountMapper.findById(1L)).thenReturn(account);
    when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
    when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);

    EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>(
        "evt-9", "ORDER_PLACED", Instant.now(), "trade-api", 1,
        new OrderPlacedPayload("order-9", 1L, "AAPL", "SELL", 100,
            new BigDecimal("149.00"), "key9", Instant.now())
    );

    service.executeOrder(event);

    verify(settlementService).fillOrder(
        eq("order-9"),
        eq(event.getPayload()),
        argThat(price -> price.compareTo(new BigDecimal("149.00")) == 0),
        eq(instrument)
    );
} 
    
    @Test
    public void testSellLimitAboveBidRejects() {
        OrderDto order = new OrderDto();
        order.setOrderId("order-10");
        order.setStatus("NEW");
        
        AccountDto account = new AccountDto();
        account.setAccountId(1L);
        account.setStatus("ACTIVE");
        
        InstrumentDto instrument = new InstrumentDto();
        instrument.setSymbol("AAPL");
        instrument.setTradable(true);
        
        // Quote: bid=149
        Quote quote = new Quote("AAPL", new BigDecimal("150"), 
            new BigDecimal("149"), new BigDecimal("151"), 
            "USD", null, null, null, "open", false, Instant.now());
        
        when(orderMapper.findById("order-10")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(quote);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>(
            "evt-10", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-10", 1L, "AAPL", "SELL", 100, 
                new BigDecimal("150.00"), "key10", Instant.now())
        );
        
        service.executeOrder(event);
        
        verify(settlementService).rejectOrder(
            "order-10",
            event.getPayload(),
            "PRICE_NOT_MET"
        );
    }
}
