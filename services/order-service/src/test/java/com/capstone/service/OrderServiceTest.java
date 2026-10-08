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
import com.capstone.exception.InsufficientHoldingsException;
import com.capstone.exception.InvalidOrderException;
import com.capstone.exception.OrderNotCancellableException;
import com.capstone.kafka.OrderPlacedEventPublisher;
import com.capstone.kafka.TransactionCommitHook;
import com.capstone.mapper.OrderMapper;
import com.capstone.dto.OrderResponse;
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
    @Mock
    private OrderPlacedEventPublisher orderPlacedEventPublisher;
    @Mock
    private TransactionCommitHook transactionCommitHook;
    @Mock
    private FxConversionService fxConversionService;
    @Mock
    private AuthOrderNotificationClient authOrderNotificationClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderMapper,
                accountService,
                instrumentService,
                orderPlacedEventPublisher,
                transactionCommitHook,
                fxConversionService,
                authOrderNotificationClient
        );
    }

    @Test
    void placeOrder_duplicateIdempotencyKey_throws() {
        PlaceOrderRequest request = request(OrderSide.BUY, 2, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.of(sampleOrder(OrderSide.BUY, OrderStatus.NEW)));

        assertThrows(DuplicateOrderException.class, () -> orderService.placeOrder(request, "idem-12345"));
        verify(transactionCommitHook, never()).runAfterCommit(any());
        verify(orderPlacedEventPublisher, never()).publishOrderPlaced(any(), anyString());
    }

    @Test
    void placeOrder_invalidQuantity_throws() {
        PlaceOrderRequest request = request(OrderSide.BUY, 0, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.getAccountByReferenceStrict("11111111-1111-4111-8111-111111111111")).thenReturn(account(new BigDecimal("100.00"), 3L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());

        assertThrows(InvalidOrderException.class, () -> orderService.placeOrder(request, "idem-12345"));
        verify(transactionCommitHook, never()).runAfterCommit(any());
        verify(orderPlacedEventPublisher, never()).publishOrderPlaced(any(), anyString());
    }

    @Test
    void placeOrder_invalidPrice_throws() {
        PlaceOrderRequest request = request(OrderSide.BUY, 2, BigDecimal.ZERO);
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.getAccountByReferenceStrict("11111111-1111-4111-8111-111111111111")).thenReturn(account(new BigDecimal("100.00"), 3L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());

        assertThrows(InvalidOrderException.class, () -> orderService.placeOrder(request, "idem-12345"));
    }

    @Test
    void placeOrder_buyInsufficientFunds_throws() {
        PlaceOrderRequest request = request(OrderSide.BUY, 20, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.getAccountByReferenceStrict("11111111-1111-4111-8111-111111111111")).thenReturn(account(new BigDecimal("50.00"), 3L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());
        when(fxConversionService.convert(new BigDecimal("200.00"), "USD", "USD")).thenReturn(new BigDecimal("200.00"));

        assertThrows(InsufficientFundsException.class, () -> orderService.placeOrder(request, "idem-12345"));
    }

    @Test
    void placeOrder_buyValid_reservesFundsAndInsertsOrder() {
        PlaceOrderRequest request = request(OrderSide.BUY, 2, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.getAccountByReferenceStrict("11111111-1111-4111-8111-111111111111")).thenReturn(account(new BigDecimal("100.00"), 7L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());
        when(fxConversionService.convert(new BigDecimal("20.00"), "USD", "USD")).thenReturn(new BigDecimal("20.00"));

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

        ArgumentCaptor<Runnable> afterCommitAction = ArgumentCaptor.forClass(Runnable.class);
        verify(transactionCommitHook).runAfterCommit(afterCommitAction.capture());
        verify(orderPlacedEventPublisher, never()).publishOrderPlaced(any(), anyString());

        afterCommitAction.getValue().run();
        verify(orderPlacedEventPublisher).publishOrderPlaced(created, "AAPL");
    }

    @Test
    void placeOrderForAuthorizedAccount_acceptsAtNew_answersNew_andDefersPublishUntilAfterCommit() {
        PlaceOrderRequest request = request(OrderSide.BUY, 2, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.verifyAuthorizedAccount("11111111-1111-4111-8111-111111111111", "11111111-1111-4111-8111-111111111111"))
                .thenReturn(account(new BigDecimal("100.00"), 7L));
        when(accountService.getAccountByReferenceStrict("11111111-1111-4111-8111-111111111111")).thenReturn(account(new BigDecimal("100.00"), 7L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());
        when(fxConversionService.convert(new BigDecimal("20.00"), "USD", "USD")).thenReturn(new BigDecimal("20.00"));

        OrderResponse response = orderService.placeOrderForAuthorizedAccount(request, "11111111-1111-4111-8111-111111111111");

        assertEquals("NEW", response.getStatus());
        assertEquals("Order NEW", response.getMessage());
        verify(orderMapper).insertOrder(any(Order.class));

        ArgumentCaptor<Runnable> afterCommitAction = ArgumentCaptor.forClass(Runnable.class);
        verify(transactionCommitHook).runAfterCommit(afterCommitAction.capture());
        verify(orderPlacedEventPublisher, never()).publishOrderPlaced(any(), anyString());

        afterCommitAction.getValue().run();
        verify(orderPlacedEventPublisher).publishOrderPlaced(any(Order.class), eq("AAPL"));
    }

    @Test
    void placeOrder_sellValid_insertsOrderWithoutBalanceUpdate() {
        PlaceOrderRequest request = request(OrderSide.SELL, 2, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.getAccountByReferenceStrict("11111111-1111-4111-8111-111111111111")).thenReturn(account(new BigDecimal("100.00"), 7L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());
        when(orderMapper.findHoldingQuantityForUpdate(1L, 200L)).thenReturn(Optional.of(new BigDecimal("5")));

        Order created = orderService.placeOrder(request, "idem-12345");

        verify(orderMapper).insertOrder(any(Order.class));
        verify(accountService, never()).updateCashBalance(anyLong(), any(), anyLong());
        verify(transactionCommitHook).runAfterCommit(any());
        assertEquals(OrderSide.SELL, created.getSide());
    }

    @Test
    void placeOrder_sellWithoutHoldings_throwsInsufficientHoldings() {
        PlaceOrderRequest request = request(OrderSide.SELL, 2, new BigDecimal("10.00"));
        when(orderMapper.findOrderByIdempotencyKey("idem-12345")).thenReturn(Optional.empty());
        when(accountService.getAccountByReferenceStrict("11111111-1111-4111-8111-111111111111")).thenReturn(account(new BigDecimal("100.00"), 7L));
        when(instrumentService.validateAndGetInstrument("AAPL")).thenReturn(instrument());
        when(orderMapper.findHoldingQuantityForUpdate(1L, 200L)).thenReturn(Optional.empty());

        assertThrows(InsufficientHoldingsException.class, () -> orderService.placeOrder(request, "idem-12345"));
        verify(orderMapper, never()).insertOrder(any(Order.class));
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
        return new PlaceOrderRequest("11111111-1111-4111-8111-111111111111", "AAPL", side, qty, price, "idem-12345");
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


