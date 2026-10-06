package com.capstone.controller;

import com.capstone.dto.OrderHistoryEntry;
import com.capstone.dto.OrderResponse;
import com.capstone.dto.PlaceOrderRequest;
import com.capstone.enums.OrderSide;

import com.capstone.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private Authentication authentication;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService);
    }

    @Test
    void placeOrder_authorizesAndMapsResponse() {
        when(authentication.getName()).thenReturn("11111111-1111-4111-8111-111111111111");
        PlaceOrderRequest request = new PlaceOrderRequest(
            "11111111-1111-4111-8111-111111111111", "AAPL", OrderSide.BUY, 3, new BigDecimal("12.00"), "idem-12345678"
        );
        OrderResponse serviceResponse = new OrderResponse(
            "ORD-abc",
            "NEW",
            "Order NEW",
            "AAPL",
            "BUY",
            3,
            new BigDecimal("12.00")
        );
        when(orderService.placeOrderForAuthorizedAccount(request, "11111111-1111-4111-8111-111111111111")).thenReturn(serviceResponse);

        ResponseEntity<OrderResponse> response = controller.placeOrder(request, authentication);

        verify(orderService).placeOrderForAuthorizedAccount(request, "11111111-1111-4111-8111-111111111111");
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
        when(authentication.getName()).thenReturn("11111111-1111-4111-8111-111111111111");
        UUID id = UUID.randomUUID();
        when(orderService.cancelOrderForAuthorizedAccount(id, "11111111-1111-4111-8111-111111111111"))
                .thenThrow(new com.capstone.exception.OrderNotFoundException(id));

        assertThrows(com.capstone.exception.OrderNotFoundException.class, () -> controller.cancelOrder(id, authentication));
    }

    @Test
    void cancelOrder_disappearsAfterCancel_throwsIllegalState() {
        when(authentication.getName()).thenReturn("11111111-1111-4111-8111-111111111111");
        UUID id = UUID.randomUUID();
        when(orderService.cancelOrderForAuthorizedAccount(id, "11111111-1111-4111-8111-111111111111"))
                .thenThrow(new IllegalStateException("Order disappeared after cancellation"));

        assertThrows(IllegalStateException.class, () -> controller.cancelOrder(id, authentication));
    }

    @Test
    void cancelOrder_success_returnsCancelledOrder() {
        when(authentication.getName()).thenReturn("11111111-1111-4111-8111-111111111111");
        UUID id = UUID.randomUUID();
        OrderResponse cancelled = new OrderResponse(
            "ORD-" + id,
            "CANCELLED",
            "Order CANCELLED",
            null,
            "BUY",
            1,
            new BigDecimal("2.00")
        );
        when(orderService.cancelOrderForAuthorizedAccount(id, "11111111-1111-4111-8111-111111111111")).thenReturn(cancelled);

        ResponseEntity<OrderResponse> response = controller.cancelOrder(id, authentication);

        verify(orderService).cancelOrderForAuthorizedAccount(id, "11111111-1111-4111-8111-111111111111");
        assertEquals("CANCELLED", response.getBody().getStatus());
    }

    @Test
    void getOrders_appliesStatusAndDateFilters() {
        when(authentication.getName()).thenReturn("11111111-1111-4111-8111-111111111111");
        OffsetDateTime from = OffsetDateTime.now().minusHours(2);
        OffsetDateTime to = OffsetDateTime.now().plusHours(1);
        OrderHistoryEntry filled = new OrderHistoryEntry(
            "ORD-2",
            "11111111-1111-4111-8111-111111111111",
            null,
            "SELL",
            2,
            new BigDecimal("10.00"),
            null,
            "FILLED",
            "k2",
            OffsetDateTime.now().minusMinutes(30)
        );
        when(orderService.getOrderHistoryForAuthorizedAccount("11111111-1111-4111-8111-111111111111", "FILLED", from, to, "11111111-1111-4111-8111-111111111111"))
            .thenReturn(List.of(filled));

        ResponseEntity<List<OrderHistoryEntry>> response = controller.getOrders("11111111-1111-4111-8111-111111111111", "FILLED", from, to, authentication);

        verify(orderService).getOrderHistoryForAuthorizedAccount("11111111-1111-4111-8111-111111111111", "FILLED", from, to, "11111111-1111-4111-8111-111111111111");
        assertEquals(1, response.getBody().size());
        assertEquals("FILLED", response.getBody().get(0).getStatus());
    }

    @Test
    void getOrders_withoutFilters_returnsAll() {
        when(authentication.getName()).thenReturn("11111111-1111-4111-8111-111111111111");
        OrderHistoryEntry o1 = new OrderHistoryEntry();
        o1.setStatus("NEW");
        OrderHistoryEntry o2 = new OrderHistoryEntry();
        o2.setStatus("CANCELLED");
        when(orderService.getOrderHistoryForAuthorizedAccount("11111111-1111-4111-8111-111111111111", null, null, null, "11111111-1111-4111-8111-111111111111"))
                .thenReturn(List.of(o1, o2));

        ResponseEntity<List<OrderHistoryEntry>> response = controller.getOrders("11111111-1111-4111-8111-111111111111", null, null, null, authentication);

        verify(orderService).getOrderHistoryForAuthorizedAccount("11111111-1111-4111-8111-111111111111", null, null, null, "11111111-1111-4111-8111-111111111111");
        assertEquals(2, response.getBody().size());
    }
}



