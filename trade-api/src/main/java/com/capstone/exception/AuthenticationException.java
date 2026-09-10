package com.capstone.exception;

/**
 * Thrown when JWT token validation fails.
 * Always returns AUTH-401 with uniform message regardless of failure type.
 * Failure types: missing header, wrong scheme, invalid signature, expired token.
 */
public final class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
