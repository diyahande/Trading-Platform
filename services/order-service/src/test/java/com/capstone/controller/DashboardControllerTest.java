package com.capstone.controller;

import com.capstone.dto.DashboardResponse;
import com.capstone.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;
    @Mock
    private Authentication authentication;

    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(dashboardService);
        when(authentication.getName()).thenReturn("acc-ref");
    }

    @Test
    void getDashboard_returnsAggregatedResponse() {
        DashboardResponse payload = new DashboardResponse(
                new BigDecimal("41010.00"),
                new BigDecimal("1010.00"),
                new BigDecimal("25000.00"),
                new BigDecimal("23180.00")
        );
        when(dashboardService.getDashboardSummaryForAuthorizedAccount("acc-ref")).thenReturn(payload);

        ResponseEntity<DashboardResponse> response = controller.getDashboard(authentication);

        verify(dashboardService).getDashboardSummaryForAuthorizedAccount("acc-ref");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(new BigDecimal("41010.00"), response.getBody().getPortfolioValue());
        assertEquals(new BigDecimal("1010.00"), response.getBody().getTodaysPnl());
        assertEquals(new BigDecimal("25000.00"), response.getBody().getAvailableCash());
        assertEquals(new BigDecimal("23180.00"), response.getBody().getBuyingPower());
    }
}