package com.capstone.service;

import com.capstone.dto.PlaceOrderRequest;
import com.capstone.entity.Account;
import com.capstone.entity.Instrument;
import com.capstone.entity.Order;
import com.capstone.enums.AccountStatus;
import com.capstone.enums.AssetClass;
import com.capstone.enums.OrderSide;
import com.capstone.enums.OrderStatus;
import com.capstone.exception.DuplicateOrderException;
import com.capstone.exception.InsufficientFundsException;
import com.capstone.exception.InvalidOrderException;
import com.capstone.exception.OrderNotCancellableException;
import com.capstone.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private AccountService accountService;
    @Mock
    private InstrumentService instrumentService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderMapper, accountService, instrumentService);
    }

    @Test
    void placeOrder_duplicateIdempotencyKey_throws() {
        PlaceOrderRequest request = request(OrderSide.BUY, 2, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.of(sampleOrder(OrderSide.BUY, OrderStatus.NEW)));

        assertThrows(DuplicateOrderException.class, () -> orderService.placeOrder(request, "idem-12345"));
    }

    @Test
    void placeOrder_invalidQuantity_throws() {
        PlaceOrderRequest request = request(OrderSide.BUY, 0, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.getAccountByIdStrict(1L)).thenReturn(account(new BigDecimal("100.00"), 3L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());

        assertThrows(InvalidOrderException.class, () -> orderService.placeOrder(request, "idem-12345"));
    }

    @Test
    void placeOrder_invalidPrice_throws() {
        PlaceOrderRequest request = request(OrderSide.BUY, 2, BigDecimal.ZERO);
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.getAccountByIdStrict(1L)).thenReturn(account(new BigDecimal("100.00"), 3L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());

        assertThrows(InvalidOrderException.class, () -> orderService.placeOrder(request, "idem-12345"));
    }

    @Test
    void placeOrder_buyInsufficientFunds_throws() {
        PlaceOrderRequest request = request(OrderSide.BUY, 20, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.getAccountByIdStrict(1L)).thenReturn(account(new BigDecimal("50.00"), 3L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());

        assertThrows(InsufficientFundsException.class, () -> orderService.placeOrder(request, "idem-12345"));
    }

    @Test
    void placeOrder_buyValid_reservesFundsAndInsertsOrder() {
        PlaceOrderRequest request = request(OrderSide.BUY, 2, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.getAccountByIdStrict(1L)).thenReturn(account(new BigDecimal("100.00"), 7L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());

        Order created = orderService.placeOrder(request, "idem-12345");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insertOrder(captor.capture());
        verify(accountService).updateCashBalance(1L, new BigDecimal("80.0000"), 7L);

        Order inserted = captor.getValue();
        assertEquals(1L, inserted.getAccountId());
        assertEquals(200L, inserted.getInstrumentId());
        assertEquals(OrderStatus.NEW, inserted.getStatus());
        assertEquals("idem-12345", inserted.getIdempotencyKey());
        assertNotNull(inserted.getOrderId());
        assertEquals(inserted, created);
    }

    @Test
    void placeOrder_sellValid_insertsOrderWithoutBalanceUpdate() {
        PlaceOrderRequest request = request(OrderSide.SELL, 2, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.getAccountByIdStrict(1L)).thenReturn(account(new BigDecimal("100.00"), 7L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());

        Order created = orderService.placeOrder(request, "idem-12345");

        verify(orderMapper).insertOrder(any(Order.class));
        verify(accountService, never()).updateCashBalance(anyLong(), any(), anyLong());
        assertEquals(OrderSide.SELL, created.getSide());
    }

    @Test
    void getOrderById_delegatesMapper() {
        UUID id = UUID.randomUUID();
        Order order = sampleOrder(OrderSide.SELL, OrderStatus.FILLED);
        when(orderMapper.findOrderById(id)).thenReturn(Optional.of(order));

        Optional<Order> result = orderService.getOrderById(id);

        assertTrue(result.isPresent());
        assertEquals(order, result.get());
    }

    @Test
    void getOrdersByAccountId_delegatesMapper() {
        List<Order> orders = List.of(sampleOrder(OrderSide.BUY, OrderStatus.NEW));
        when(orderMapper.findOrdersByAccountId(1L)).thenReturn(orders);

        List<Order> result = orderService.getOrdersByAccountId(1L);

        assertEquals(1, result.size());
    }

    @Test
    void cancelOrder_successWhenRowUpdated() {
        UUID id = UUID.randomUUID();
        when(orderMapper.updateOrderStatusConditional(id, OrderStatus.NEW, OrderStatus.CANCELLED)).thenReturn(1);

        assertDoesNotThrow(() -> orderService.cancelOrder(id));
    }

    @Test
    void cancelOrder_throwsWhenNoRowUpdated() {
        UUID id = UUID.randomUUID();
        when(orderMapper.updateOrderStatusConditional(id, OrderStatus.NEW, OrderStatus.CANCELLED)).thenReturn(0);

        assertThrows(OrderNotCancellableException.class, () -> orderService.cancelOrder(id));
    }

    @Test
    void getOrderByIdempotencyKey_delegatesMapper() {
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());

        Optional<Order> result = orderService.getOrderByIdempotencyKey("idem-12345");

        assertTrue(result.isEmpty());
    }

    @Test
    void updateOrderStatus_delegatesMapper() {
        UUID id = UUID.randomUUID();

        orderService.updateOrderStatus(id, OrderStatus.FILLED);

        verify(orderMapper).updateOrderStatus(id, OrderStatus.FILLED);
    }

    private static PlaceOrderRequest request(OrderSide side, Integer qty, BigDecimal price) {
        return new PlaceOrderRequest(1L, "AAPL", side, qty, price, "idem-12345");
    }

    private static Account account(BigDecimal cash, Long version) {
        return new Account(
                1L,
                1L,
                "acc-1",
                "Trader",
                "USD",
                cash,
                AccountStatus.ACTIVE,
                version,
                Instant.parse("2024-01-01T00:00:00Z")
        );
    }

    private static Instrument instrument() {
        return new Instrument(
                200L,
                "AAPL",
                "Apple",
                AssetClass.EQUITY,
                "USD",
                false,
                Instant.parse("2024-01-01T00:00:00Z")
        );
    }

    private static Order sampleOrder(OrderSide side, OrderStatus status) {
        return new Order(
                UUID.randomUUID(),
                1L,
                "acc-1",
                200L,
                side,
                new BigDecimal("2"),
                new BigDecimal("10.00"),
                status,
                "idem-x",
                Instant.parse("2024-01-01T00:00:00Z")
        );
    }
}


