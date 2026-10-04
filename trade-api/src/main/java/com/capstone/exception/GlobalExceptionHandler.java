package com.capstone.exception;

import com.capstone.dto.ErrorResponse;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final HttpStatus HTTP_422 = HttpStatus.valueOf(422);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex) {
        HttpStatus status = mapHttpStatus(ex.getCatalogueCode());

        log.warn(
                "Domain exception handled: code={}, type={}, message={}",
                ex.getCatalogueCode(),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
        );

        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(ex.getCatalogueCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Request body validation failed: {}", ex.getMessage(), ex);

        return ResponseEntity
                .status(HTTP_422)
                .body(new ErrorResponse("VAL-422", "Invalid input"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint validation failed: {}", ex.getMessage(), ex);

        return ResponseEntity
                .status(HTTP_422)
                .body(new ErrorResponse("VAL-422", "Invalid input"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No resource found: method={}, path={}", ex.getHttpMethod(), ex.getResourcePath());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("GEN-404", "Not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
        log.error("Unexpected exception while handling request", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("GEN-500", "Internal server error"));
    }

    private HttpStatus mapHttpStatus(String errorCode) {
        return switch (errorCode) {
            case "ACC-404" -> HttpStatus.NOT_FOUND;
            case "ACC-403" -> HttpStatus.FORBIDDEN;
            case "INS-404" -> HttpStatus.NOT_FOUND;
            case "ORD-404" -> HttpStatus.NOT_FOUND;
            case "ORD-400" -> HttpStatus.BAD_REQUEST;
            case "ORD-409" -> HttpStatus.CONFLICT;
            case "VAL-422" -> HTTP_422;
            case "AUTH-401" -> HttpStatus.UNAUTHORIZED;
            case "MKT-503" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}



