package com.capstone.watchlist;

import com.capstone.dto.CreatePriceAlertRequest;
import com.capstone.dto.PriceAlertResponse;
import com.capstone.entity.Account;
import com.capstone.entity.PriceAlert;
import com.capstone.entity.Watchlist;
import com.capstone.enums.AccountStatus;
import com.capstone.exception.AlertLimitExceededException;
import com.capstone.exception.AlertResourceNotFoundException;
import com.capstone.mapper.PriceAlertMapper;
import com.capstone.mapper.WatchlistMapper;
import com.capstone.service.AccountService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertServiceTest {

    @Mock
    private PriceAlertMapper priceAlertMapper;

    @Mock
    private WatchlistMapper watchlistMapper;

    @Mock
    private AccountService accountService;

    @Mock
    private AlertNotificationPort alertNotificationPort;

    private PriceAlertService priceAlertService;

    @BeforeEach
    void setUp() {
        priceAlertService = new PriceAlertService(
                priceAlertMapper,
                watchlistMapper,
                accountService,
                alertNotificationPort
        );
    }

    @Test
    void createAlert_watchlistMissing_throwsNotFound() {
        when(accountService.getAccountByReferenceStrict("acc-1")).thenReturn(account(9L));
        when(watchlistMapper.findById(123L)).thenReturn(null);

        CreatePriceAlertRequest request = new CreatePriceAlertRequest(
                123L,
                new BigDecimal("150.00"),
                "ABOVE"
        );

        assertThrows(
                AlertResourceNotFoundException.class,
                () -> priceAlertService.createAlert("acc-1", request)
        );
    }

    @Test
    void createAlert_capExceeded_throwsConflict() {
        when(accountService.getAccountByReferenceStrict("acc-1")).thenReturn(account(9L));
        when(watchlistMapper.findById(123L)).thenReturn(watchlist(123L, 9L));
        when(priceAlertMapper.countActiveByUserId(9L)).thenReturn(100);

        CreatePriceAlertRequest request = new CreatePriceAlertRequest(
                123L,
                new BigDecimal("150.00"),
                "ABOVE"
        );

        assertThrows(
                AlertLimitExceededException.class,
                () -> priceAlertService.createAlert("acc-1", request)
        );
    }

    @Test
    void createAlert_valid_insertsActiveAlert() {
        when(accountService.getAccountByReferenceStrict("acc-1")).thenReturn(account(9L));
        when(watchlistMapper.findById(123L)).thenReturn(watchlist(123L, 9L));
        when(priceAlertMapper.countActiveByUserId(9L)).thenReturn(0);

        CreatePriceAlertRequest request = new CreatePriceAlertRequest(
                123L,
                new BigDecimal("150.00"),
                "ABOVE"
        );

        PriceAlertResponse response = priceAlertService.createAlert("acc-1", request);

        ArgumentCaptor<PriceAlert> captor = ArgumentCaptor.forClass(PriceAlert.class);
        verify(priceAlertMapper).insert(captor.capture());

        PriceAlert inserted = captor.getValue();
        assertEquals(123L, inserted.getWatchlistId());
        assertEquals(new BigDecimal("150.00"), inserted.getThreshold());
        assertEquals(PriceAlert.Direction.ABOVE, inserted.getDirection());
        assertEquals(PriceAlert.Status.ACTIVE, inserted.getStatus());

        assertEquals("ABOVE", response.getDirection());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void deactivateAlert_alertMissing_throwsNotFound() {
        when(accountService.getAccountByReferenceStrict("acc-1")).thenReturn(account(9L));
        when(priceAlertMapper.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
                AlertResourceNotFoundException.class,
                () -> priceAlertService.deactivateAlert("acc-1", 999L)
        );
    }

    @Test
    void evaluateAlertsForSymbol_deliveryFailure_marksDeliveryFailed() throws Exception {
        PriceAlert activeAlert = new PriceAlert(
                1L,
                123L,
                new BigDecimal("150.00"),
                PriceAlert.Direction.ABOVE,
                PriceAlert.Status.ACTIVE,
                Instant.now(),
                null,
                Instant.now()
        );

        when(priceAlertMapper.findActiveBySymbol("ACME")).thenReturn(List.of(activeAlert));
        when(watchlistMapper.findById(123L)).thenReturn(watchlist(123L, 9L));
        doThrow(new AlertNotificationPort.AlertDeliveryException("provider down"))
                .when(alertNotificationPort)
                .deliverPriceAlert(any(), any(), any(), any(), any(), any());

        priceAlertService.evaluateAlertsForSymbol("ACME", new BigDecimal("151.00"));

        ArgumentCaptor<PriceAlert> captor = ArgumentCaptor.forClass(PriceAlert.class);
        verify(priceAlertMapper, org.mockito.Mockito.times(2)).updateStatus(captor.capture());

        List<PriceAlert> updates = captor.getAllValues();
        assertEquals(PriceAlert.Status.TRIGGERED, updates.get(0).getStatus());
        assertEquals(PriceAlert.Status.DELIVERY_FAILED, updates.get(1).getStatus());
    }

    private static Account account(Long userId) {
        return new Account(
                1L,
                userId,
                "acc-1",
                "Trader",
                "USD",
                new BigDecimal("1000.00"),
                AccountStatus.ACTIVE,
                1L,
                Instant.now()
        );
    }

    private static Watchlist watchlist(Long watchlistId, Long userId) {
        return new Watchlist(
                watchlistId,
                userId,
                200L,
                "Test",
                Instant.now()
        );
    }
}
