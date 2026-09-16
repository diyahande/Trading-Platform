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
        when(authentication.getName()).thenReturn("11111111-1111-4111-8111-111111111111");
    }

    @Test
    void getAccount_mapsResponse() {
        AccountResponse accountResponse = new AccountResponse(
            1L,
            "11111111-1111-4111-8111-111111111111",
            "Trader",
            new BigDecimal("50.00"),
            "ACTIVE",
            7,
            LocalDateTime.now()
        );
        when(accountService.getAccountResponseForAuthorizedAccount("11111111-1111-4111-8111-111111111111", "11111111-1111-4111-8111-111111111111")).thenReturn(accountResponse);

        ResponseEntity<AccountResponse> response = controller.getAccount("11111111-1111-4111-8111-111111111111", authentication);

        verify(accountService).getAccountResponseForAuthorizedAccount("11111111-1111-4111-8111-111111111111", "11111111-1111-4111-8111-111111111111");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        assertEquals("11111111-1111-4111-8111-111111111111", response.getBody().getAccountId());
        assertEquals("ACTIVE", response.getBody().getStatus());
        assertEquals(7, response.getBody().getVersion());
        assertNotNull(response.getBody().getLastUpdated());
    }

    @Test
    void getBalance_mapsResponse() {
        BalanceResponse balanceResponse = new BalanceResponse(
            "11111111-1111-4111-8111-111111111111",
            new BigDecimal("12.34"),
            "USD",
            LocalDateTime.now()
        );
        when(accountService.getBalanceResponseForAuthorizedAccount("11111111-1111-4111-8111-111111111111", "11111111-1111-4111-8111-111111111111")).thenReturn(balanceResponse);

        ResponseEntity<BalanceResponse> response = controller.getBalance("11111111-1111-4111-8111-111111111111", authentication);

        verify(accountService).getBalanceResponseForAuthorizedAccount("11111111-1111-4111-8111-111111111111", "11111111-1111-4111-8111-111111111111");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("11111111-1111-4111-8111-111111111111", response.getBody().getAccountId());
        assertEquals(new BigDecimal("12.34"), response.getBody().getCashBalance());
        assertEquals("USD", response.getBody().getCurrency());
        assertNotNull(response.getBody().getAsOf());
    }

    @Test
    void getPositions_returnsServicePayload() {
        List<PositionResponse> positions = List.of(new PositionResponse("11111111-1111-4111-8111-111111111111", "AAPL", 10, new BigDecimal("100.00")));
        when(accountService.getPositionsForAuthorizedAccount("11111111-1111-4111-8111-111111111111", "11111111-1111-4111-8111-111111111111")).thenReturn(positions);

        ResponseEntity<List<PositionResponse>> response = controller.getPositions("11111111-1111-4111-8111-111111111111", authentication);

        verify(accountService).getPositionsForAuthorizedAccount("11111111-1111-4111-8111-111111111111", "11111111-1111-4111-8111-111111111111");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }
}


