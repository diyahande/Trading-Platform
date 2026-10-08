package com.capstone.watchlist;

import com.capstone.entity.Account;
import com.capstone.enums.AccountStatus;
import com.capstone.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthPriceAlertNotificationClientTest {

    @Mock
    private AccountService accountService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthPriceAlertNotificationClient client;

    @BeforeEach
    void setUp() {
        client = new AuthPriceAlertNotificationClient(objectMapper, accountService);
        ReflectionTestUtils.setField(client, "authServiceBaseUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(client, "priceAlertPath", "/auth/notifications/price-alert");
        ReflectionTestUtils.setField(client, "internalToken", "thisistheinternalservicesecret");
    }

    @Test
    void deliverPriceAlert_missingActiveAccount_throwsDeliveryException() {
        when(accountService.getActiveAccountsByUserId(9L)).thenReturn(List.of());

        assertThrows(
                AlertNotificationPort.AlertDeliveryException.class,
                () -> client.deliverPriceAlert(
                        1L,
                        9L,
                        "AAPL",
                        new BigDecimal("150.00"),
                        new BigDecimal("150.00"),
                        "ABOVE"
                )
        );
    }

    @Test
    void jsonDeserialization_handlesExtraFieldsFromAuthService() throws Exception {
        String authServiceResponseBody = """
                {
                    "alertId": 8,
                    "accountReference": "11111111-1111-4111-8111-111111111111",
                    "recipient": "pes1202201311@pesu.pes.edu",
                    "emailSent": true
                }
                """;

        Class<?> responseClass = Class.forName("com.capstone.watchlist.AuthPriceAlertNotificationClient$PriceAlertNotificationResponse");
        Object parsed = objectMapper.readValue(authServiceResponseBody, responseClass);
        org.junit.jupiter.api.Assertions.assertNotNull(parsed);
    }
}
