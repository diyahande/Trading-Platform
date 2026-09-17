package com.capstone.executor.error;

import org.springframework.stereotype.Component;

/**
 * Manages bounded retry logic with exponential backoff.
 *
 * Retry delays:
 *
 * Attempt 1 -> 100 ms
 * Attempt 2 -> 200 ms
 * Attempt 3 -> 400 ms
 *
 * After the retry budget is exhausted, the message is sent to DLT.
 */
@Component
public class RetryPolicy {

    private static final int MAX_RETRIES = 3;

    private static final long INITIAL_BACKOFF_MS = 100;

    private static final long MAX_BACKOFF_MS = 5000;

    private static final double BACKOFF_MULTIPLIER = 2.0;

    public static final String RETRY_COUNT_HEADER =
            "x-retry-count";

    public static final String RETRY_REASON_HEADER =
            "x-retry-reason";

    /**
     * Returns true when another retry is allowed.
     *
     * retryCount = 0 -> retry allowed
     * retryCount = 1 -> retry allowed
     * retryCount = 2 -> retry allowed
     * retryCount = 3 -> no more retries
     */
    public boolean shouldRetry(int retryCount) {
        return retryCount < MAX_RETRIES;
    }

    /**
     * Calculates exponential backoff.
     */
    public long getBackoffMs(int retryCount) {

        long backoff = (long) (
                INITIAL_BACKOFF_MS
                        * Math.pow(
                                BACKOFF_MULTIPLIER,
                                retryCount
                        )
        );

        return Math.min(backoff, MAX_BACKOFF_MS);
    }

    /**
     * Parses the retry count from a Kafka header.
     */
    public int parseRetryCount(String retryCountHeader) {

        if (retryCountHeader == null
                || retryCountHeader.isBlank()) {

            return 0;
        }

        try {

            int retryCount = Integer.parseInt(
                    retryCountHeader
            );

            return Math.max(retryCount, 0);

        } catch (NumberFormatException e) {

            return 0;
        }
    }

    public int getMaxRetries() {
        return MAX_RETRIES;
    }
}