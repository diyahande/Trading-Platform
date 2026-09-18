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
import com.capstone.executor.client.FauxnanceQuoteClient;
import com.capstone.executor.service.OrderExecutionService;
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
    @Mock private PortfolioMapper portfolioMapper;
    @Mock private PortfolioHoldingsMapper portfolioHoldingsMapper;
    @Mock private TradeEventPublisher tradeEventPublisher;
    private OrderExecutionService service;
    private List<EventEnvelope<TradeEventPayload>> publishedEvents;
    
    @BeforeEach
    public void setup() {
        publishedEvents = new ArrayList<>();
        lenient().doAnswer(inv -> { publishedEvents.add(inv.getArgument(0)); return null; })
            .when(tradeEventPublisher).publish(any());
        service = new OrderExecutionService(quoteClient, orderMapper, accountMapper, instrumentMapper, portfolioMapper, portfolioHoldingsMapper, tradeEventPublisher);
    }
    
    // Integration Test Case A: Fill
    @Test
    public void testIntegrationFillBuyOrder() {
        // Setup: valid order, ACTIVE account, tradable instrument
        OrderDto order = new OrderDto("order-fill-buy", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("233.00"), "NEW", "key-fill", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); 
        account.setAccountId(1L); 
        account.setStatus("ACTIVE"); 
        account.setVersion(0L);  // Critical: set version to 0 so version guard matches
        account.setCashBalance(new BigDecimal("100000"));
        
        InstrumentDto instrument = new InstrumentDto(); 
        instrument.setInstrumentId(1L);  // Must match instrumentId in order
        instrument.setSymbol("AAPL"); 
        instrument.setTradable(true);
        
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        
        when(orderMapper.findById(eq("order-fill-buy"))).thenReturn(order);
        when(accountMapper.findById(eq(1L))).thenReturn(account);
        when(instrumentMapper.findBySymbol(eq("AAPL"))).thenReturn(instrument);
        when(quoteClient.fetchQuote(eq("AAPL"))).thenReturn(quote);
        when(orderMapper.updateToFilled(anyString(), any(), any())).thenReturn(1);
        
        // Mock portfolio and portfolio holdings for order settlement
        PortfolioDto portfolio = new PortfolioDto();
        portfolio.setPortfolioId(200L);
        when(portfolioMapper.findByAccountId(eq(1L))).thenReturn(portfolio);
        when(portfolioHoldingsMapper.findByPortfolioAndInstrument(eq(200L), anyLong())).thenReturn(null);
        lenient().doNothing().when(portfolioHoldingsMapper).insert(anyLong(), anyLong(), any(), any());
        
        // Mock account cash update with version guard (returns 1 for success)
        when(accountMapper.updateCashWithVersionGuard(eq(1L), any(BigDecimal.class), eq(0L))).thenReturn(1);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-fill", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-fill-buy", 1L, "AAPL", "BUY", 100, new BigDecimal("233.00"), "key-fill", Instant.now()));
        
        service.executeOrder(event);
        
        // Verify fill outcome
        assertEquals(1, publishedEvents.size());
        TradeEventPayload payload = publishedEvents.get(0).getPayload();
        assertEquals("ORDER_FILLED", publishedEvents.get(0).getEventType());
        assertEquals("FILLED", payload.getStatus());
        assertNull(payload.getReason());
        assertNotNull(payload.getExecutedPrice());
        assertEquals(1L, payload.getAccountId());
        
        // Verify order updated to FILLED
        verify(orderMapper).updateToFilled(eq("order-fill-buy"), eq(payload.getExecutedPrice()), any());
    }
    
    // Integration Test Case B: Reject (non-marketable limit)
    @Test
    public void testIntegrationRejectNonMarketableOrder() {
        // Setup: order outside marketable range
        OrderDto order = new OrderDto("order-reject", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("231.00"), "NEW", "key-reject", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); account.setAccountId(1L); account.setStatus("ACTIVE"); account.setCashBalance(new BigDecimal("100000"));
        InstrumentDto instrument = new InstrumentDto(); instrument.setSymbol("AAPL"); instrument.setTradable(true);
        Quote quote = new Quote("AAPL", new BigDecimal("232.50"), new BigDecimal("232.40"), new BigDecimal("232.60"), "USD", null, null, null, "open", false, Instant.now());
        
        when(orderMapper.findById(eq("order-reject"))).thenReturn(order);
        when(accountMapper.findById(eq(1L))).thenReturn(account);
        when(instrumentMapper.findBySymbol(eq("AAPL"))).thenReturn(instrument);
        when(quoteClient.fetchQuote(eq("AAPL"))).thenReturn(quote);
        when(orderMapper.updateToRejected(anyString(), anyString())).thenReturn(1);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-reject", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-reject", 1L, "AAPL", "BUY", 100, new BigDecimal("231.00"), "key-reject", Instant.now()));
        
        service.executeOrder(event);
        
        // Verify rejection outcome
        assertEquals(1, publishedEvents.size());
        TradeEventPayload payload = publishedEvents.get(0).getPayload();
        assertEquals("ORDER_REJECTED", publishedEvents.get(0).getEventType());
        assertEquals("REJECTED", payload.getStatus());
        assertEquals("PRICE_NOT_MET", payload.getReason());
        assertNull(payload.getExecutedPrice());
        
        // Verify order updated to REJECTED
        verify(orderMapper).updateToRejected("order-reject", "PRICE_NOT_MET");
    }
    
    // Integration Test Case C: Pricing unavailable (Fauxnance outage)
    @Test
    public void testIntegrationPricingUnavailableAfterRetries() {
        // Setup: Fauxnance returns null after retries
        OrderDto order = new OrderDto("order-pricing-na", 1L, "acc-1", 1L, "BUY", 100, new BigDecimal("233.00"), "NEW", "key-pna", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); account.setAccountId(1L); account.setStatus("ACTIVE"); account.setCashBalance(new BigDecimal("100000"));
        InstrumentDto instrument = new InstrumentDto(); instrument.setSymbol("AAPL"); instrument.setTradable(true);
        
        when(orderMapper.findById("order-pricing-na")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(quoteClient.fetchQuote("AAPL")).thenReturn(null);
        when(orderMapper.updateToRejected(anyString(), anyString())).thenReturn(1);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-pna", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-pricing-na", 1L, "AAPL", "BUY", 100, new BigDecimal("233.00"), "key-pna", Instant.now()));
        
        service.executeOrder(event);
        
        // Verify rejection outcome with pricing unavailable reason
        assertEquals(1, publishedEvents.size());
        TradeEventPayload payload = publishedEvents.get(0).getPayload();
        assertEquals("ORDER_REJECTED", publishedEvents.get(0).getEventType());
        assertEquals("REJECTED", payload.getStatus());
        assertEquals("PRICING_UNAVAILABLE", payload.getReason());
        
        // Verify order was NOT left at NEW - it was updated to REJECTED
        verify(orderMapper).updateToRejected("order-pricing-na", "PRICING_UNAVAILABLE");
    }
    
    // Integration test: FauxnanceQuoteClient retry behavior
    @Test
    public void testFauxnanceRetryBehavior() {
        // This test verifies the quote client retries on 5xx and eventually returns null
        // The client is tested separately but we verify integration with service
        
        // Setup: quote client will return null after retries
        OrderDto order = new OrderDto("order-retry", 1L, "acc-1", 1L, "SELL", 50, new BigDecimal("232.00"), "NEW", "key-retry", Instant.now(), null, null, null);
        AccountDto account = new AccountDto(); account.setAccountId(1L); account.setStatus("ACTIVE"); account.setCashBalance(new BigDecimal("100000"));
        InstrumentDto instrument = new InstrumentDto(); instrument.setSymbol("MSFT"); instrument.setTradable(true);
        
        when(orderMapper.findById("order-retry")).thenReturn(order);
        when(accountMapper.findById(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("MSFT")).thenReturn(instrument);
        when(quoteClient.fetchQuote("MSFT")).thenReturn(null);
        when(orderMapper.updateToRejected(anyString(), anyString())).thenReturn(1);
        
        EventEnvelope<OrderPlacedPayload> event = new EventEnvelope<>("evt-retry", "ORDER_PLACED", Instant.now(), "trade-api", 1,
            new OrderPlacedPayload("order-retry", 1L, "MSFT", "SELL", 50, new BigDecimal("232.00"), "key-retry", Instant.now()));
        
        service.executeOrder(event);
        
        // Verify order was rejected after retry budget exhausted
        assertEquals(1, publishedEvents.size());
        assertEquals("PRICING_UNAVAILABLE", publishedEvents.get(0).getPayload().getReason());
        
        // Verify quote client was called
        verify(quoteClient).fetchQuote("MSFT");
    }
}
