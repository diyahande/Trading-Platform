package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error response envelope for all failures.
 * Maps to OpenAPI ErrorResponse schema.
 * 
 * Clients MUST branch on errorCode, never on message and never on HTTP status alone,
 * since 404 and 409 each carry two error codes.
 * 
 * Error codes and their meanings:
 * - ACC-404: Account not found
 * - ACC-403: Account not active, or not reachable with this token
 * - INS-404: Instrument not found or not tradable
 * - ORD-400: Insufficient funds
 * - ORD-409: Insufficient holdings, duplicate order, or order not cancellable
 * - VAL-422: Invalid input
 * - AUTH-401: Unauthorised or invalid token
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /**
     * Machine-readable error code.
     * Valid values: ACC-404, ACC-403, INS-404, ORD-400, ORD-409, VAL-422, AUTH-401
     */
    private String errorCode;

    /**
     * Human-readable error message (display only).
     * Never include stack traces, SQL fragments, class names, or internal identifiers.
     * Leaking those is OWASP A05.
     */
    private String message;
}
