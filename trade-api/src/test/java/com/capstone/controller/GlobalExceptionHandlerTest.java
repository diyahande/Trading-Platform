package com.capstone.controller;

import com.capstone.dto.ErrorResponse;
import com.capstone.enums.AccountStatus;
import com.capstone.exception.AccountNotActiveException;
import com.capstone.exception.AccountNotFoundException;
import com.capstone.exception.DomainException;
import com.capstone.exception.DuplicateOrderException;
import com.capstone.exception.InsufficientFundsException;
import com.capstone.exception.InstrumentNotFoundException;
import com.capstone.exception.InvalidOrderException;
import com.capstone.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private static final HttpStatus HTTP_422 = HttpStatus.valueOf(422);

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @ParameterizedTest
    @MethodSource("domainExceptionCases")
    void domainExceptions_mapToExpectedStatusAndCode(DomainException exception, HttpStatus expectedStatus, String expectedCode) {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(exception);

        assertEquals(expectedStatus, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expectedCode, response.getBody().getErrorCode());
        assertNotNull(response.getBody().getMessage());
    }

    @Test
    void unexpectedExceptions_mapToSanitized500Response() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(new RuntimeException("DB blew up"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("GEN-500", response.getBody().getErrorCode());
        assertEquals("Internal server error", response.getBody().getMessage());
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> domainExceptionCases() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(new AccountNotFoundException(1001L), HttpStatus.NOT_FOUND, "ACC-404"),
                org.junit.jupiter.params.provider.Arguments.of(new AccountNotActiveException(1001L, AccountStatus.SUSPENDED), HttpStatus.FORBIDDEN, "ACC-403"),
                org.junit.jupiter.params.provider.Arguments.of(new InstrumentNotFoundException("INFY"), HttpStatus.NOT_FOUND, "INS-404"),
                org.junit.jupiter.params.provider.Arguments.of(new InsufficientFundsException(1001L, new BigDecimal("100.00"), new BigDecimal("50.00")), HttpStatus.BAD_REQUEST, "ORD-400"),
                org.junit.jupiter.params.provider.Arguments.of(new DuplicateOrderException("idem-12345"), HttpStatus.CONFLICT, "ORD-409"),
                org.junit.jupiter.params.provider.Arguments.of(new InvalidOrderException("quantity"), HTTP_422, "VAL-422"),
                org.junit.jupiter.params.provider.Arguments.of(new UnauthorizedException(), HttpStatus.UNAUTHORIZED, "AUTH-401")
        );
    }
}



