package com.capstone.executor.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;

/**
 * Classifies failures into two categories:
 *
 * UNRECOVERABLE:
 * The message/data will never succeed and should go to the DLT immediately.
 *
 * RECOVERABLE:
 * The failure may succeed later and should be retried with backoff.
 */
@Component
public class FailureClassifier {

    private static final Logger logger =
            LoggerFactory.getLogger(FailureClassifier.class);

    public static class FailureClassification {

        private final boolean recoverable;
        private final String reason;

        public FailureClassification(
                boolean recoverable,
                String reason) {

            this.recoverable = recoverable;
            this.reason = reason;
        }

        public boolean isRecoverable() {
            return recoverable;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Classifies a processing exception.
     */
    public FailureClassification classify(
            Exception exception,
            String message) {

        /*
         * ---------------------------------------------------------
         * 1. MALFORMED JSON
         * ---------------------------------------------------------
         */
        if (containsCause(exception, JsonProcessingException.class)) {

            return new FailureClassification(
                    false,
                    "MALFORMED_JSON: " + safeMessage(exception)
            );
        }

        /*
         * ---------------------------------------------------------
         * 2. MISSING / INVALID REQUIRED DATA
         * ---------------------------------------------------------
         */
        String exceptionMessage = safeMessage(exception);

        if (exception instanceof IllegalArgumentException) {

            if (containsIgnoreCase(exceptionMessage, "orderId")) {

                return new FailureClassification(
                        false,
                        "MISSING_ORDER_ID: " + exceptionMessage
                );
            }

            if (containsIgnoreCase(exceptionMessage, "eventType")) {

                return new FailureClassification(
                        false,
                        "UNEXPECTED_EVENT_TYPE: " + exceptionMessage
                );
            }

            return new FailureClassification(
                    false,
                    "INVALID_DATA: " + exceptionMessage
            );
        }

        /*
         * ---------------------------------------------------------
         * 3. UNKNOWN ORDER
         * ---------------------------------------------------------
         */
        if (containsIgnoreCase(exceptionMessage, "order not found")
                || containsIgnoreCase(exceptionMessage, "order not exist")
                || containsIgnoreCase(exceptionMessage, "unknown order")) {

            return new FailureClassification(
                    false,
                    "ORDER_NOT_FOUND: " + exceptionMessage
            );
        }

        /*
         * ---------------------------------------------------------
         * 4. NETWORK / TIMEOUT FAILURE (check before database)
         * ---------------------------------------------------------
         */
        if (containsCause(exception, ConnectException.class)
                || containsCause(exception, SocketTimeoutException.class)
                || containsCause(exception, IOException.class)
                || containsIgnoreCase(exceptionMessage, "timeout")
                || containsIgnoreCase(exceptionMessage, "timed out")) {

            return new FailureClassification(
                    true,
                    "NETWORK_UNAVAILABLE: " + exceptionMessage
            );
        }

        /*
         * ---------------------------------------------------------
         * 5. DATABASE CONNECTION FAILURE
         * ---------------------------------------------------------
         */
        if (containsCause(exception, SQLException.class)
                || containsCause(exception, DataAccessException.class)
                || containsIgnoreCase(exceptionMessage, "connection")
                || containsIgnoreCase(exceptionMessage, "database unavailable")
                || containsIgnoreCase(exceptionMessage, "could not obtain connection")
                || containsIgnoreCase(exceptionMessage, "connection refused")) {

            return new FailureClassification(
                    true,
                    "DATABASE_UNAVAILABLE: " + exceptionMessage
            );
        }

        /*
         * ---------------------------------------------------------
         * 6. OPTIMISTIC LOCK / CONCURRENT UPDATE
         * ---------------------------------------------------------
         */
        if (containsIgnoreCase(exceptionMessage, "optimistic")
                || containsIgnoreCase(exceptionMessage, "lock")
                || containsIgnoreCase(exceptionMessage, "concurrent update")
                || containsIgnoreCase(exceptionMessage, "affected rows")) {

            return new FailureClassification(
                    true,
                    "LOCK_CONFLICT: " + exceptionMessage
            );
        }

        /*
         * ---------------------------------------------------------
         * 7. DEFAULT
         *
         * Unknown failures are treated as permanent so that a
         * poison message cannot retry forever.
         * ---------------------------------------------------------
         */
        logger.warn(
                "Unknown exception classified as unrecoverable: {}",
                exception.getClass().getSimpleName()
        );

        return new FailureClassification(
                false,
                "UNKNOWN_ERROR: "
                        + exception.getClass().getSimpleName()
        );
    }

    /**
     * Checks the complete exception cause chain.
     */
    private boolean containsCause(
            Throwable exception,
            Class<? extends Throwable> type) {

        Throwable current = exception;

        while (current != null) {

            if (type.isInstance(current)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private boolean containsIgnoreCase(
            String value,
            String search) {

        return value != null
                && search != null
                && value.toLowerCase()
                        .contains(search.toLowerCase());
    }

    private String safeMessage(Throwable exception) {

        if (exception == null) {
            return "unknown failure";
        }

        if (exception.getMessage() != null) {
            return exception.getMessage();
        }

        return exception.getClass().getSimpleName();
    }
}