package com.capstone.exception;

import com.capstone.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @ParameterizedTest
    @CsvSource({
            "ACC-404,404",
            "ACC-403,403",
            "INS-404,404",
            "ORD-404,404",
            "ORD-400,400",
            "ORD-409,409",
            "VAL-422,422",
            "AUTH-401,401",
            "UNK-000,500"
    })
    void handleDomainException_mapsCatalogueCodeToExpectedStatus(String code, int expectedStatus) {
        DomainException ex = exceptionForCode(code);

        ResponseEntity<ErrorResponse> response = handler.handleDomainException(ex);

        assertEquals(expectedStatus, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(code, response.getBody().getErrorCode());
    }

    @Test
    void handleMethodArgumentNotValid_returns422() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getMessage()).thenReturn("invalid");

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.valueOf(422), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VAL-422", response.getBody().getErrorCode());
    }

    @Test
    void handleConstraintViolation_returns422() {
        ConstraintViolationException ex = new ConstraintViolationException("invalid", Set.of());

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

        assertEquals(HttpStatus.valueOf(422), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VAL-422", response.getBody().getErrorCode());
    }

    @Test
    void handleUnexpectedException_returns500() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("GEN-500", response.getBody().getErrorCode());
    }

    private static DomainException exceptionForCode(String code) {
        return switch (code) {
            case "ACC-404" -> new AccountNotFoundException(1L);
            case "ACC-403" -> new AccountNotActiveException(1L, com.capstone.enums.AccountStatus.SUSPENDED);
            case "INS-404" -> new InstrumentNotFoundException("AAPL");
            case "ORD-404" -> new OrderNotFoundException(UUID.randomUUID());
            case "ORD-400" -> new InsufficientFundsException(1L, new BigDecimal("10"), new BigDecimal("1"));
            case "ORD-409" -> new DuplicateOrderException("k");
            case "VAL-422" -> new InvalidOrderException("price");
            case "AUTH-401" -> new UnauthorizedException();
            default -> new DomainException(code, "unknown") { };
        };
    }
}


