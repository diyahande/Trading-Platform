package com.capstone.controller;

import com.capstone.dto.OrderHistoryEntry;
import com.capstone.dto.OrderResponse;
import com.capstone.dto.PlaceOrderRequest;
import com.capstone.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/api/v1/orders")
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
            orderService.placeOrderForAuthorizedAccount(request, authentication.getName())
        );
    }


    @DeleteMapping("/api/v1/orders/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(
                orderService.cancelOrderForAuthorizedAccount(id, authentication.getName())
        );
    }

    
    @GetMapping("/api/v1/accounts/{accountRef}/orders")
    public ResponseEntity<List<OrderHistoryEntry>> getOrders(
            @PathVariable String accountRef,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Authentication authentication) {
        return ResponseEntity.ok(
                orderService.getOrderHistoryForAuthorizedAccount(accountRef, status, from, to, authentication.getName())
        );
    }

}
