package com.capstone.controller;

import com.capstone.dto.PnlResponseDto;
import com.capstone.dto.PortfolioSummaryDto;
import com.capstone.dto.PricedPositionDto;
import com.capstone.exception.AccountNotAccessibleException;
import com.capstone.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private Authentication authentication;

    private PortfolioController controller;

    @BeforeEach
    void setUp() {
        controller = new PortfolioController(portfolioService);
    }

    @Test
    void getPortfolioSummary_returnsPayloadForMatchingAccount() {
        Jwt jwt = new Jwt("token", OffsetDateTime.now().toInstant(), OffsetDateTime.now().plusMinutes(30).toInstant(),
                Map.of("alg", "HS256"), Map.of("accountId", "acc-ref-1"));
        when(authentication.getPrincipal()).thenReturn(jwt);

        PortfolioSummaryDto payload = new PortfolioSummaryDto(
                "acc-ref-1",
                "USD",
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                new BigDecimal("150.00"),
                new BigDecimal("50.00"),
                new BigDecimal("33.33"),
                new BigDecimal("5.00"),
                new BigDecimal("300.00"),
                1,
                false,
                OffsetDateTime.now()
        );
        when(portfolioService.getPortfolioSummary("acc-ref-1")).thenReturn(payload);

        ResponseEntity<PortfolioSummaryDto> response = controller.getPortfolioSummary("acc-ref-1", authentication);

        verify(portfolioService).getPortfolioSummary("acc-ref-1");
        assertEquals(200, response.getStatusCode().value());
        assertEquals(payload, response.getBody());
    }

    @Test
    void getPricedPositions_throwsWhenTokenAccountDoesNotMatchPath() {
        Jwt jwt = new Jwt("token", OffsetDateTime.now().toInstant(), OffsetDateTime.now().plusMinutes(30).toInstant(),
                Map.of("alg", "HS256"), Map.of("accountId", "acc-ref-2"));
        when(authentication.getPrincipal()).thenReturn(jwt);

        assertThrows(AccountNotAccessibleException.class,
                () -> controller.getPricedPositions("acc-ref-1", null, authentication));
    }

    @Test
    void getPnl_returnsPayload() {
        Jwt jwt = new Jwt("token", OffsetDateTime.now().toInstant(), OffsetDateTime.now().plusMinutes(30).toInstant(),
                Map.of("alg", "HS256"), Map.of("accountId", "acc-ref-1"));
        when(authentication.getPrincipal()).thenReturn(jwt);

        PnlResponseDto pnl = new PnlResponseDto(
                "acc-ref-1",
                "USD",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                new BigDecimal("30.00"),
                List.of(),
                OffsetDateTime.now()
        );
        when(portfolioService.getPnl("acc-ref-1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), true)).thenReturn(pnl);

        ResponseEntity<PnlResponseDto> response = controller.getPnl(
                "acc-ref-1",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                true,
                authentication
        );

        verify(portfolioService).getPnl("acc-ref-1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), true);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(pnl, response.getBody());
    }
}
