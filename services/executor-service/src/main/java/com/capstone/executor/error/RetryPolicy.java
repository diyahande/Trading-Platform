package com.capstone.executor.error;

import org.springframework.stereotype.Component;


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

    
    public boolean shouldRetry(int retryCount) {
        return retryCount < MAX_RETRIES;
    }

   
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