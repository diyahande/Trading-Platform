package com.capstone.watchlist;

import java.math.BigDecimal;

/**
 * Published interface for watchlist to notify customers of triggered price alerts.
 * Implemented by the notifications module.
 * 
 * This is an in-process call: no timeout, but can fail if channel unknown or provider refuses.
 */
public interface AlertNotificationPort {
    
    /**
     * Deliver a price alert notification to a customer.
     * 
    * @param alertId the triggered alert identifier
     * @param userId the customer
     * @param symbol the instrument symbol that triggered the alert
     * @param currentPrice the current price at which the alert triggered
     * @param threshold the threshold that was configured
     * @param direction "ABOVE" or "BELOW" - the trigger direction
     * 
     * @throws AlertDeliveryException if the channel cannot be resolved or delivery fails
     */
    void deliverPriceAlert(
            Long alertId,
            Long userId,
            String symbol,
            BigDecimal currentPrice,
            BigDecimal threshold,
            String direction
    ) throws AlertDeliveryException;

    class AlertDeliveryException extends Exception {
        public AlertDeliveryException(String message) {
            super(message);
        }

        public AlertDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
