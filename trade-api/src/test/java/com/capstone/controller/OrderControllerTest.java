package com.capstone.controller;

import com.capstone.dto.OrderHistoryEntry;
import com.capstone.dto.OrderResponse;
import com.capstone.dto.PlaceOrderRequest;
import com.capstone.entity.Order;
import com.capstone.enums.OrderSide;
import com.capstone.enums.OrderStatus;
import com.capstone.exception.OrderNotFoundException;
import com.capstone.service.AccountService;
import com.capstone.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private AccountService accountService;
    @Mock
    private Authentication authentication;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService, accountService);
    }

    @Test
    void placeOrder_authorizesAndMapsResponse() {
        when(authentication.getName()).thenReturn("acc-1");
        PlaceOrderRequest request = new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 3, new BigDecimal("12.00"), "idem-12345678"
        );
        Order order = order(OrderSide.BUY, OrderStatus.NEW, Instant.parse("2024-01-01T00:00:00Z"), "idem-12345678");
        when(orderService.placeOrder(request, "idem-12345678")).thenReturn(order);

        ResponseEntity<OrderResponse> response = controller.placeOrder(request, authentication);

        verify(accountService).verifyAuthorizedAccount(1L, "acc-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getOrderId().startsWith("ORD-"));
        assertEquals("NEW", response.getBody().getStatus());
        assertEquals("Order NEW", response.getBody().getMessage());
        assertEquals("AAPL", response.getBody().getSymbol());
        assertEquals("BUY", response.getBody().getSide());
    }

    @Test
    void cancelOrder_notFound_throwsDomainException() {
        UUID id = UUID.randomUUID();
        when(orderService.getOrderById(id)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> controller.cancelOrder(id, authentication));
    }

    @Test
    void cancelOrder_disappearsAfterCancel_throwsIllegalState() {
        when(authentication.getName()).thenReturn("acc-1");
        UUID id = UUID.randomUUID();
        Order existing = order(OrderSide.BUY, OrderStatus.NEW, Instant.now(), "idem-1");
        when(orderService.getOrderById(id)).thenReturn(Optional.of(existing), Optional.empty());

        assertThrows(IllegalStateException.class, () -> controller.cancelOrder(id, authentication));
    }

    @Test
    void cancelOrder_success_returnsCancelledOrder() {
        when(authentication.getName()).thenReturn("acc-1");
        UUID id = UUID.randomUUID();
        Order existing = new Order(id, 1L, "acc-1", 2L, OrderSide.BUY, new BigDecimal("1"), new BigDecimal("2.00"),
                OrderStatus.NEW, "idem-1", Instant.now());
        Order cancelled = new Order(id, 1L, "acc-1", 2L, OrderSide.BUY, new BigDecimal("1"), new BigDecimal("2.00"),
                OrderStatus.CANCELLED, "idem-1", Instant.now());
        when(orderService.getOrderById(id)).thenReturn(Optional.of(existing), Optional.of(cancelled));

        ResponseEntity<OrderResponse> response = controller.cancelOrder(id, authentication);

        verify(accountService).verifyAuthorizedAccount(1L, "acc-1");
        verify(orderService).cancelOrder(id);
        assertEquals("CANCELLED", response.getBody().getStatus());
    }

    @Test
    void getOrders_appliesStatusAndDateFilters() {
        when(authentication.getName()).thenReturn("acc-1");
        Instant now = Instant.now();
        Order newOrder = order(OrderSide.BUY, OrderStatus.NEW, now.minusSeconds(600), "k1");
        Order filledOrder = order(OrderSide.SELL, OrderStatus.FILLED, now.minusSeconds(60), "k2");
        when(orderService.getOrdersByAccountId(1L)).thenReturn(List.of(newOrder, filledOrder));

        LocalDateTime from = LocalDateTime.ofInstant(now.minusSeconds(120), ZoneId.systemDefault());
        LocalDateTime to = LocalDateTime.ofInstant(now.plusSeconds(30), ZoneId.systemDefault());

        ResponseEntity<List<OrderHistoryEntry>> response = controller.getOrders(1L, "FILLED", from, to, authentication);

        verify(accountService).verifyAuthorizedAccount(1L, "acc-1");
        assertEquals(1, response.getBody().size());
        assertEquals("FILLED", response.getBody().get(0).getStatus());
    }

    @Test
    void getOrders_withoutFilters_returnsAll() {
        when(authentication.getName()).thenReturn("acc-1");
        Order o1 = order(OrderSide.BUY, OrderStatus.NEW, Instant.now(), "k1");
        Order o2 = order(OrderSide.SELL, OrderStatus.CANCELLED, Instant.now(), "k2");
        when(orderService.getOrdersByAccountId(1L)).thenReturn(List.of(o1, o2));

        ResponseEntity<List<OrderHistoryEntry>> response = controller.getOrders(1L, null, null, null, authentication);

        assertEquals(2, response.getBody().size());
    }

    private static Order order(OrderSide side, OrderStatus status, Instant createdAt, String key) {
        return new Order(
                UUID.randomUUID(),
                1L,
                "acc-1",
                2L,
                side,
                new BigDecimal("3"),
                new BigDecimal("12.00"),
                status,
                key,
                createdAt
        );
    }
}



