package com.capstone.executor.integration;

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
import com.capstone.executor.mapper.PortfolioMapper;
import com.capstone.executor.mapper.PortfolioHoldingsMapper;
import com.capstone.executor.mapper.TransactionMapper;
import com.capstone.executor.client.FauxnanceQuoteClient;
import com.capstone.executor.service.OrderExecutionService;
import com.capstone.executor.service.SettlementService;
import com.capstone.executor.service.TradeEventPublisher;
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

// Integration test: executor against mocked Fauxnance covering fill/reject/pricing-unavailable
@ExtendWith(MockitoExtension.class)
public class OrderExecutionIntegrationTest {
    
    @Mock private FauxnanceQuoteClient quoteClient;
    @Mock private OrderMapper orderMapper;
    @Mock private AccountMapper accountMapper;
    @Mock private InstrumentMapper instrumentMapper;
    @Mock private SettlementService settlementService;
    @Mock private TradeEventPublisher tradeEventPublisher;
    
    private OrderExecutionService service;
    
    @BeforeEach
    public void setup() {
        service = new OrderExecutionService(quoteClient, orderMapper, accountMapper, instrumentMapper, settlementService, tradeEventPublisher);
    }
    
    // Integration Test Case A: Fill
    @Test
    public void testIntegrationFillBuyOrder() {
        // Setup: valid order, ACTIVE account, tradable instrument
        OrderDto order = new OrderDto("order-fill-buy", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("233.00"), "NEW", "key-fill", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); 
        account.setAccountId(1L); 
        account.setStatus("ACTIVE"); 
        account.setCashBalance(new BigDecimal("100000"));
        
        InstrumentDto instrument = new InstrumentDto(); 
        instrument.setInstrumentId(1L);
        instrument.setSymbol("AAPL"); 
        instrument.setTradable(true);
        
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        
        when(orderMapper.findById(eq("order-fill-buy"))).thenReturn(order);
        when(accountMapper.findById(eq(1L))).thenReturn(account);
        when(instrumentMapper.findBySymbol(eq("AAPL"))).thenReturn(instrument);
        when(quoteClient.fetchQuote(eq("AAPL"))).thenReturn(quote);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-fill", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-fill-buy", 1L, "AAPL", "BUY", 100, new BigDecimal("233.00"), "key-fill", Instant.now()));
        
        service.executeOrder(event);
        
        // Verify fillOrder was called with correct parameters
        verify(settlementService).fillOrder(
            eq("order-fill-buy"),
            any(OrderPlacedPayload.class),
            eq(new BigDecimal("232.60")),  // ask price
            eq(instrument)
        );
    }
    
    // Integration Test Case B: Reject (non-marketable limit)
    @Test
    public void testIntegrationRejectNonMarketableOrder() {
        // Setup: order outside marketable range
        OrderDto order = new OrderDto("order-reject", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("231.00"), "NEW", "key-reject", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); 
        account.setAccountId(1L); 
        account.setStatus("ACTIVE"); 
        account.setCashBalance(new BigDecimal("100000"));
        InstrumentDto instrument = new InstrumentDto(); 
        instrument.setSymbol("AAPL"); 
        instrument.setTradable(true);
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        
        when(orderMapper.findById(eq("order-reject"))).thenReturn(order);
        when(accountMapper.findById(eq(1L))).thenReturn(account);
        when(instrumentMapper.findBySymbol(eq("AAPL"))).thenReturn(instrument);
        when(quoteClient.fetchQuote(eq("AAPL"))).thenReturn(quote);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-reject", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-reject", 1L, "AAPL", "BUY", 100, new BigDecimal("231.00"), "key-reject", Instant.now()));
        
        service.executeOrder(event);
        
        // Verify rejectOrder was called with PRICE_NOT_MET reason
        verify(settlementService).rejectOrder(
            eq("order-reject"),
            any(OrderPlacedPayload.class),
            eq("PRICE_NOT_MET")
        );
    }
    
    // Integration Test Case C: Pricing unavailable (Fauxnance outage) - RECOVERABLE
    @Test
    public void testIntegrationPricingUnavailableAfterRetries() {
       
        OrderDto order = new OrderDto("order-pricing-na", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("233.00"), "NEW", "key-pna", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); 
        account.setAccountId(1L); 
        account.setStatus("ACTIVE"); 
        account.setCashBalance(new BigDecimal("100000"));
        InstrumentDto instrument = new InstrumentDto(); 
        instrument.setSymbol("AAPL"); 
        instrument.setTradable(true);
        
        when(orderMapper.findById("order-pricing-na")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(null);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-pna", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-pricing-na", 1L, "AAPL", "BUY", 100, new BigDecimal("233.00"), "key-pna", Instant.now()));
        
        // Should throw exception (recoverable error) instead of rejecting
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.executeOrder(event));
        assertTrue(ex.getMessage().contains("Quote unavailable"), "Exception should mention quote unavailable");
        
        // Verify NO settlement was called
        verify(settlementService, never()).fillOrder(any(), any(), any(), any());
        verify(settlementService, never()).rejectOrder(any(), any(), any());
    }
    
    
    // Integration test: FauxnanceQuoteClient retry behavior - RECOVERABLE
    @Test
    public void testFauxnanceRetryBehavior() {
        
        OrderDto order = new OrderDto("order-retry", 1L, "acc-1", 1L, "SELL", 50, new BigDecimal("232.00"), "NEW", "key-retry", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); 
        account.setAccountId(1L); 
        account.setStatus("ACTIVE"); 
        account.setCashBalance(new BigDecimal("100000"));
        InstrumentDto instrument = new InstrumentDto(); 
        instrument.setSymbol("MSFT"); 
        instrument.setTradable(true);
        
        when(orderMapper.findById("order-retry")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("MSFT")).thenReturn(instrument);
        when(quoteClient.fetchQuote("MSFT")).thenReturn(null);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-retry", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-retry", 1L, "MSFT", "SELL", 50, new BigDecimal("232.00"), "key-retry", Instant.now()));
        
        // Should throw exception instead of rejecting
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.executeOrder(event));
        assertTrue(ex.getMessage().contains("Quote unavailable"), "Exception should mention quote unavailable");
        
        // Verify NO settlement was called - message will be retried
        verify(settlementService, never()).fillOrder(any(), any(), any(), any());
        verify(settlementService, never()).rejectOrder(any(), any(), any());
        
        // Verify quote client was called
        verify(quoteClient).fetchQuote("MSFT");
    }
}

