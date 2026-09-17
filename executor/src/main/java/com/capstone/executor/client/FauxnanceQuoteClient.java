package com.capstone.executor.client;

import com.capstone.executor.domain.FauxnanceQuoteResponse;
import com.capstone.executor.domain.Quote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class FauxnanceQuoteClient {

    private static final Logger logger =
            LoggerFactory.getLogger(FauxnanceQuoteClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final int maxRetries;
    private final long retryDelayMs;

    public FauxnanceQuoteClient(
            @Value("${fauxnance.base-url}") String baseUrl,
            @Value("${fauxnance.api-key:}") String apiKey,
            @Value("${fauxnance.max-retries:3}") int maxRetries,
            @Value("${fauxnance.retry-delay-ms:100}") long retryDelayMs,
            ObjectMapper objectMapper) {

        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        logger.info(
                "FauxnanceQuoteClient initialized: baseUrl={}, maxRetries={}, retryDelayMs={}, apiKeyConfigured={}",
                baseUrl,
                maxRetries,
                retryDelayMs,
                apiKey != null && !apiKey.isBlank()
        );
    }

    public Quote fetchQuote(String symbol) {

        if (symbol == null || symbol.isBlank()) {
            logger.warn("Quote requested for null/empty symbol");
            return null;
        }

        int attempt = 0;
        long delay = retryDelayMs;

        while (attempt < maxRetries) {

            try {

                String url = String.format(
                        "%s/quotes/%s",
                        baseUrl,
                        symbol
                );

                logger.info(
                        "Requesting Fauxnance quote: symbol={}, url={}, attempt={}/{}",
                        symbol,
                        url,
                        attempt + 1,
                        maxRetries
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .header("X-Api-Key", apiKey)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                int status = response.statusCode();
                String body = response.body();

                logger.info(
                        "Fauxnance response: symbol={} status={} body={}",
                        symbol,
                        status,
                        body
                );

                // -------------------------------------------------
                // SUCCESS
                // -------------------------------------------------
                if (status >= 200 && status < 300) {

                    try {

                        /*
                         * Fauxnance response contains:
                         *
                         * {
                         *     "data": {
                         *         "symbol": "AAPL",
                         *         "price": ...,
                         *         "bid": ...,
                         *         "ask": ...
                         *     }
                         * }
                         *
                         * Therefore deserialize into
                         * FauxnanceQuoteResponse first.
                         */

                        FauxnanceQuoteResponse wrapper =
                                objectMapper.readValue(
                                        body,
                                        FauxnanceQuoteResponse.class
                                );

                        Quote quote = wrapper.getData();

                        if (quote == null) {

                            logger.warn(
                                    "Fauxnance returned a response with null data: symbol={}",
                                    symbol
                            );

                            return null;
                        }

                        logger.info(
                                "Parsed quote: symbol={} price={} bid={} ask={} currency={} marketState={} stale={} quoteAsOf={}",
                                quote.getSymbol(),
                                quote.getPrice(),
                                quote.getBid(),
                                quote.getAsk(),
                                quote.getCurrency(),
                                quote.getMarketState(),
                                quote.getQuoteAsOf()
                        );

                        if (quote.getPrice() == null) {
                            logger.warn(
                                    "Fauxnance quote has null price: symbol={}",
                                    symbol
                            );
                        }

                        if (quote.getBid() == null) {
                            logger.warn(
                                    "Fauxnance quote has null bid: symbol={}",
                                    symbol
                            );
                        }

                        if (quote.getAsk() == null) {
                            logger.warn(
                                    "Fauxnance quote has null ask: symbol={}",
                                    symbol
                            );
                        }

                        return quote;

                    } catch (Exception parseException) {

                        /*
                         * This is a response parsing problem,
                         * NOT a network problem.
                         */
                        logger.error(
                                "Could not parse Fauxnance response: symbol={} body={} error={}",
                                symbol,
                                body,
                                parseException.getMessage(),
                                parseException
                        );

                        return null;
                    }
                }

                // -------------------------------------------------
                // 4xx
                // -------------------------------------------------
                if (status >= 400 && status < 500) {

                    logger.warn(
                            "Fauxnance 4xx: symbol={} status={} body={}",
                            symbol,
                            status,
                            body
                    );

                    return null;
                }

                // -------------------------------------------------
                // 5xx
                // -------------------------------------------------
                if (status >= 500) {

                    logger.warn(
                            "Fauxnance 5xx: symbol={} status={} body={} attempt={}/{}",
                            symbol,
                            status,
                            body,
                            attempt + 1,
                            maxRetries
                    );

                    attempt++;

                    if (attempt < maxRetries) {
                        Thread.sleep(delay);
                        delay *= 2;
                    }

                    continue;
                }

                logger.warn(
                        "Unexpected Fauxnance HTTP status: symbol={} status={} body={}",
                        symbol,
                        status,
                        body
                );

                return null;

            } catch (java.net.http.HttpTimeoutException e) {

                logger.warn(
                        "Quote timeout: symbol={} attempt={}/{} error={}",
                        symbol,
                        attempt + 1,
                        maxRetries,
                        e.getMessage()
                );

                attempt++;

                if (attempt < maxRetries) {

                    try {
                        Thread.sleep(delay);
                        delay *= 2;
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }

            } catch (java.io.IOException e) {

                logger.warn(
                        "Quote network error: symbol={} attempt={}/{} error={}",
                        symbol,
                        attempt + 1,
                        maxRetries,
                        e.getMessage()
                );

                attempt++;

                if (attempt < maxRetries) {

                    try {
                        Thread.sleep(delay);
                        delay *= 2;
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                logger.warn(
                        "Quote request interrupted: symbol={}",
                        symbol
                );

                return null;

            } catch (Exception e) {

                logger.error(
                        "Unexpected Fauxnance error: symbol={} error={}",
                        symbol,
                        e.getMessage(),
                        e
                );

                return null;
            }
        }

        logger.error(
                "Quote retry budget exhausted: symbol={}",
                symbol
        );

        return null;
    }
}