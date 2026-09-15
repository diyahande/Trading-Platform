package com.capstone.controller;

import com.capstone.dto.AccountResponse;
import com.capstone.dto.BalanceResponse;
import com.capstone.dto.PositionResponse;
import com.capstone.entity.Account;
import com.capstone.enums.AccountStatus;
import com.capstone.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
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
        Account account = account();
        when(accountService.getAccountByIdStrict(1L)).thenReturn(account);

        ResponseEntity<AccountResponse> response = controller.getAccount(1L, authentication);

        verify(accountService).verifyAuthorizedAccount(1L, "acc-1");
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
        when(accountService.getAccountBalance(1L)).thenReturn(new BigDecimal("12.34"));

        ResponseEntity<BalanceResponse> response = controller.getBalance(1L, authentication);

        verify(accountService).verifyAuthorizedAccount(1L, "acc-1");
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
        when(accountService.getPositionsByAccountId(1L)).thenReturn(positions);

        ResponseEntity<List<PositionResponse>> response = controller.getPositions(1L, authentication);

        verify(accountService).verifyAuthorizedAccount(1L, "acc-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    private static Account account() {
        return new Account(
                1L,
                10L,
                "acc-1",
                "Trader",
                "USD",
                new BigDecimal("50.00"),
                AccountStatus.ACTIVE,
                7L,
                Instant.parse("2024-01-01T00:00:00Z")
        );
    }
}


