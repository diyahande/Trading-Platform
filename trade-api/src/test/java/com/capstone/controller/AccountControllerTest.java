package com.capstone.controller;

import com.capstone.dto.AccountResponse;
import com.capstone.dto.BalanceResponse;
import com.capstone.dto.PositionResponse;
import com.capstone.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;
    @Mock
    private Authentication authentication;

    private AccountController controller;

    @BeforeEach
    void setUp() {
        controller = new AccountController(accountService);
        when(authentication.getName()).thenReturn("acc-1");
    }

    @Test
    void getAccount_mapsResponse() {
        AccountResponse accountResponse = new AccountResponse(
            1L,
            "acc-1",
            "Trader",
            new BigDecimal("50.00"),
            "ACTIVE",
            7,
            LocalDateTime.now()
        );
        when(accountService.getAccountResponseForAuthorizedAccount(1L, "acc-1")).thenReturn(accountResponse);

        ResponseEntity<AccountResponse> response = controller.getAccount(1L, authentication);

        verify(accountService).getAccountResponseForAuthorizedAccount(1L, "acc-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        assertEquals("acc-1", response.getBody().getAccountId());
        assertEquals("ACTIVE", response.getBody().getStatus());
        assertEquals(7, response.getBody().getVersion());
        assertNotNull(response.getBody().getLastUpdated());
    }

    @Test
    void getBalance_mapsResponse() {
        BalanceResponse balanceResponse = new BalanceResponse(
            1L,
            new BigDecimal("12.34"),
            "USD",
            LocalDateTime.now()
        );
        when(accountService.getBalanceResponseForAuthorizedAccount(1L, "acc-1")).thenReturn(balanceResponse);

        ResponseEntity<BalanceResponse> response = controller.getBalance(1L, authentication);

        verify(accountService).getBalanceResponseForAuthorizedAccount(1L, "acc-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getAccountId());
        assertEquals(new BigDecimal("12.34"), response.getBody().getCashBalance());
        assertEquals("USD", response.getBody().getCurrency());
        assertNotNull(response.getBody().getAsOf());
    }

    @Test
    void getPositions_returnsServicePayload() {
        List<PositionResponse> positions = List.of(new PositionResponse(1L, "AAPL", 10, new BigDecimal("100.00")));
        when(accountService.getPositionsForAuthorizedAccount(1L, "acc-1")).thenReturn(positions);

        ResponseEntity<List<PositionResponse>> response = controller.getPositions(1L, authentication);

        verify(accountService).getPositionsForAuthorizedAccount(1L, "acc-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }
}


