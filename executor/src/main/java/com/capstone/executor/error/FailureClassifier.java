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

    public FailureClassification classify(
            Exception exception,
            String message) {

        if (containsCause(exception, JsonProcessingException.class)) {

            return new FailureClassification(
                    false,
                    "MALFORMED_JSON: " + safeMessage(exception)
            );
        }

     
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

      
        if (containsIgnoreCase(exceptionMessage, "order not found")
                || containsIgnoreCase(exceptionMessage, "order not exist")
                || containsIgnoreCase(exceptionMessage, "unknown order")) {

            return new FailureClassification(
                    false,
                    "ORDER_NOT_FOUND: " + exceptionMessage
            );
        }

       
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

       
        if (containsIgnoreCase(exceptionMessage, "optimistic")
                || containsIgnoreCase(exceptionMessage, "lock")
                || containsIgnoreCase(exceptionMessage, "concurrent update")
                || containsIgnoreCase(exceptionMessage, "affected rows")) {

            return new FailureClassification(
                    true,
                    "LOCK_CONFLICT: " + exceptionMessage
            );
        }

   
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