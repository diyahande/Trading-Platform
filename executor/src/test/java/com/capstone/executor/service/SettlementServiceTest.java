package com.capstone.executor.service;

import com.capstone.executor.event.OrderPlacedPayload;
import com.capstone.executor.mapper.AccountDto;
import com.capstone.executor.mapper.AccountMapper;
import com.capstone.executor.mapper.InstrumentDto;
import com.capstone.executor.mapper.OrderMapper;
import com.capstone.executor.mapper.PortfolioDto;
import com.capstone.executor.mapper.PortfolioHoldingsDto;
import com.capstone.executor.mapper.PortfolioHoldingsMapper;
import com.capstone.executor.mapper.PortfolioMapper;
import com.capstone.executor.mapper.TransactionMapper;
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
public class SettlementServiceTest {
    
    @Mock private OrderMapper orderMapper;
    @Mock private AccountMapper accountMapper;
    @Mock private PortfolioMapper portfolioMapper;
    @Mock private PortfolioHoldingsMapper portfolioHoldingsMapper;
    @Mock private TransactionMapper transactionMapper;
    
    private SettlementService settlementService;
    
    @BeforeEach
    public void setup() {
        settlementService = new SettlementService(
            orderMapper,
            accountMapper,
            portfolioMapper,
            portfolioHoldingsMapper,
            transactionMapper
        );
    }
    

    @Test
    public void testFillBuyOrderUpdatesAttempted() {
        String orderId = "order-1";
        OrderPlacedPayload payload = new OrderPlacedPayload(
            orderId, 1L, "AAPL", "BUY", 100,
            new BigDecimal("233.00"), "key1", Instant.now()
        );
        InstrumentDto instrument = new InstrumentDto();
        instrument.setInstrumentId(1L);
        instrument.setSymbol("AAPL");
        
        BigDecimal executionPrice = new BigDecimal("232.50");
        
        // Setup mocks
        when(orderMapper.updateToFilled(anyString(), any(BigDecimal.class), any(Instant.class)))
            .thenReturn(1);
        
        AccountDto account = new AccountDto();
        account.setAccountId(1L);
        account.setStatus("ACTIVE");
        account.setCashBalance(new BigDecimal("100000"));
        account.setVersion(0L);
        
        when(accountMapper.findById(1L))
            .thenReturn(account);
        
        when(accountMapper.updateCashWithVersionGuard(anyLong(), any(BigDecimal.class), anyLong()))
            .thenReturn(1);
        
        PortfolioDto portfolio = new PortfolioDto();
        portfolio.setPortfolioId(100L);
        when(portfolioMapper.findByAccountId(1L))
            .thenReturn(portfolio);
        
        when(portfolioHoldingsMapper.findByPortfolioAndInstrument(anyLong(), anyLong()))
            .thenReturn(null);
        
        doNothing().when(portfolioHoldingsMapper).insert(anyLong(), anyLong(), any(BigDecimal.class), any(BigDecimal.class));
        
        doNothing().when(transactionMapper).insert(anyString(), anyLong(), any(BigDecimal.class), any(BigDecimal.class), anyString());
        
        // Execute - will fail on TransactionSynchronizationManager (expected without Spring context)
        try {
            settlementService.fillOrder(orderId, payload, executionPrice, instrument);
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("No active transaction")) throw e;
        }
        
        // Verify DB updates were attempted
        verify(orderMapper).updateToFilled(anyString(), any(BigDecimal.class), any(Instant.class));
        verify(accountMapper).updateCashWithVersionGuard(anyLong(), any(BigDecimal.class), anyLong());
    }
    
 
    @Test
    public void testFillSellOrderUpdatesAttempted() {
        String orderId = "order-2";
        OrderPlacedPayload payload = new OrderPlacedPayload(
            orderId, 1L, "AAPL", "SELL", 50,
            new BigDecimal("232.00"), "key2", Instant.now()
        );
        InstrumentDto instrument = new InstrumentDto();
        instrument.setInstrumentId(1L);
        
        BigDecimal executionPrice = new BigDecimal("232.50");
        
        when(orderMapper.updateToFilled(anyString(), any(BigDecimal.class), any(Instant.class)))
            .thenReturn(1);
        
        AccountDto account = new AccountDto();
        account.setAccountId(1L);
        account.setStatus("ACTIVE");
        account.setCashBalance(new BigDecimal("50000"));
        account.setVersion(1L);
        
        when(accountMapper.findById(1L))
            .thenReturn(account);
        
        when(accountMapper.updateCashWithVersionGuard(anyLong(), any(BigDecimal.class), anyLong()))
            .thenReturn(1);
        
        PortfolioDto portfolio = new PortfolioDto();
        portfolio.setPortfolioId(100L);
        when(portfolioMapper.findByAccountId(1L))
            .thenReturn(portfolio);
        
        PortfolioHoldingsDto holding = new PortfolioHoldingsDto();
        holding.setQuantity(new BigDecimal("100"));
        holding.setAveragePrice(new BigDecimal("230.00"));
        
        when(portfolioHoldingsMapper.findByPortfolioAndInstrument(anyLong(), anyLong()))
            .thenReturn(holding);
        
        when(portfolioHoldingsMapper.update(anyLong(), anyLong(), any(BigDecimal.class), any(BigDecimal.class)))
            .thenReturn(1);
        
        doNothing().when(transactionMapper).insert(anyString(), anyLong(), any(BigDecimal.class), any(BigDecimal.class), anyString());
        
        // Execute
        try {
            settlementService.fillOrder(orderId, payload, executionPrice, instrument);
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("No active transaction")) throw e;
        }
        
        // Verify SELL cash update
        verify(accountMapper).updateCashWithVersionGuard(anyLong(), any(BigDecimal.class), anyLong());
    }
    
   
    @Test
    public void testFillBuyWithInsufficientFundsThrows() {
        String orderId = "order-3";
        OrderPlacedPayload payload = new OrderPlacedPayload(
            orderId, 1L, "AAPL", "BUY", 100,
            new BigDecimal("233.00"), "key3", Instant.now()
        );
        InstrumentDto instrument = new InstrumentDto();
        instrument.setInstrumentId(1L);
        
        BigDecimal executionPrice = new BigDecimal("232.50");
        
        when(orderMapper.updateToFilled(anyString(), any(BigDecimal.class), any(Instant.class)))
            .thenReturn(1);
        
        AccountDto account = new AccountDto();
        account.setAccountId(1L);
        account.setStatus("ACTIVE");
        account.setCashBalance(new BigDecimal("5000"));  // Not enough
        account.setVersion(0L);
        
        when(accountMapper.findById(1L))
            .thenReturn(account);
        
        // Execute and expect exception
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> settlementService.fillOrder(orderId, payload, executionPrice, instrument)
        );
        
        assertTrue(ex.getMessage().contains("Insufficient funds"));
    }
    
   
    @Test
    public void testFillSellWithoutPositionThrows() {
        String orderId = "order-4";
        OrderPlacedPayload payload = new OrderPlacedPayload(
            orderId, 1L, "AAPL", "SELL", 50,
            new BigDecimal("232.00"), "key4", Instant.now()
        );
        InstrumentDto instrument = new InstrumentDto();
        instrument.setInstrumentId(1L);
        
        BigDecimal executionPrice = new BigDecimal("232.50");
        
        when(orderMapper.updateToFilled(anyString(), any(BigDecimal.class), any(Instant.class)))
            .thenReturn(1);
        
        AccountDto account = new AccountDto();
        account.setAccountId(1L);
        account.setStatus("ACTIVE");
        account.setCashBalance(new BigDecimal("50000"));
        account.setVersion(0L);
        
        when(accountMapper.findById(1L))
            .thenReturn(account);
        
        when(accountMapper.updateCashWithVersionGuard(anyLong(), any(BigDecimal.class), anyLong()))
            .thenReturn(1);
        
        PortfolioDto portfolio = new PortfolioDto();
        portfolio.setPortfolioId(100L);
        when(portfolioMapper.findByAccountId(1L))
            .thenReturn(portfolio);
        
        // No holding exists
        when(portfolioHoldingsMapper.findByPortfolioAndInstrument(anyLong(), anyLong()))
            .thenReturn(null);
        
        // Execute and expect exception
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> settlementService.fillOrder(orderId, payload, executionPrice, instrument)
        );
        
        assertTrue(ex.getMessage().contains("Insufficient position"));
    }
    
 
    @Test
    public void testRejectOrderUpdatesStatus() {
        String orderId = "order-5";
        OrderPlacedPayload payload = new OrderPlacedPayload(
            orderId, 1L, "AAPL", "BUY", 100,
            new BigDecimal("233.00"), "key5", Instant.now()
        );
        
        when(orderMapper.updateToRejected(anyString(), anyString()))
            .thenReturn(1);
        
        // Execute
        try {
            settlementService.rejectOrder(orderId, payload, "PRICE_NOT_MET");
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("No active transaction")) throw e;
        }
        
        // Verify rejection
        verify(orderMapper).updateToRejected(anyString(), anyString());
    }
    
  
    @Test
public void testFillBuyIdempotentNoOp() {
    String orderId = "order-6";

    OrderPlacedPayload payload = new OrderPlacedPayload(
        orderId,
        1L,
        "AAPL",
        "BUY",
        100,
        new BigDecimal("233.00"),
        "key6",
        Instant.now()
    );

    InstrumentDto instrument = new InstrumentDto();
    instrument.setInstrumentId(1L);

    BigDecimal executionPrice = new BigDecimal("232.50");

    // Simulate order already filled
    when(orderMapper.updateToFilled(
        anyString(),
        any(BigDecimal.class),
        any(Instant.class)
    )).thenReturn(0);

    // Execute
    try {
        settlementService.fillOrder(
            orderId,
            payload,
            executionPrice,
            instrument
        );
    } catch (IllegalStateException e) {
        if (!e.getMessage().contains("No active transaction")) {
            throw e;
        }
    }

    // Verify order update was attempted
    verify(orderMapper).updateToFilled(
        anyString(),
        any(BigDecimal.class),
        any(Instant.class)
    );

    // No cash update should be attempted
    verify(accountMapper, never()).updateCashWithVersionGuard(
        anyLong(),
        any(BigDecimal.class),
        anyLong()
    );
}
}