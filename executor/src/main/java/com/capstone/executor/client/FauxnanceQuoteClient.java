package com.capstone.executor.client;

import com.capstone.executor.domain.FauxnanceQuoteResponse;
import com.capstone.executor.domain.Quote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

            
                if (status >= 200 && status < 300) {

                    try {

                

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

              
                if (status >= 400 && status < 500) {

                    logger.warn(
                            "Fauxnance 4xx: symbol={} status={} body={}",
                            symbol,
                            status,
                            body
                    );

                    return null;
                }

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


    public Map<String, Quote> fetchQuotesBatch(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            logger.warn("Batch quote requested with null/empty symbols");
            return Map.of();
        }

        if (symbols.size() > 25) {
            logger.warn(
                    "Batch quote requested with {} symbols, exceeds 25 symbol limit. Truncating.",
                    symbols.size()
            );
            symbols = symbols.stream().limit(25).collect(Collectors.toList());
        }

        int attempt = 0;
        long delay = retryDelayMs;

        while (attempt < maxRetries) {
            try {
                String symbolsCsv = String.join(",", symbols);
                String url = String.format(
                        "%s/quotes?symbols=%s",
                        baseUrl,
                        symbolsCsv
                );

                logger.info(
                        "Requesting Fauxnance batch quotes: symbols={} count={} url={} attempt={}/{}",
                        symbolsCsv,
                        symbols.size(),
                        url,
                        attempt + 1,
                        maxRetries
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("X-Api-Key", apiKey)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                int status = response.statusCode();
                String body = response.body();

                logger.info(
                        "Fauxnance batch response: count={} status={} bodyLength={}",
                        symbols.size(),
                        status,
                        body.length()
                );

               
                if (status >= 200 && status < 300) {
                    try {
                        
                        logger.info("Fauxnance batch response body (first 500 chars): {}", 
                                body.length() > 500 ? body.substring(0, 500) : body);
                        
                        com.fasterxml.jackson.databind.JsonNode root =
                                objectMapper.readTree(body);
                        com.fasterxml.jackson.databind.JsonNode dataObject =
                                root.get("data");

                        // Check if data is an object (not an array)
                        if (dataObject == null || !dataObject.isObject()) {
                            logger.warn(
                                    "Fauxnance batch response data is not an object: count={}",
                                    symbols.size()
                            );
                            Map<String, Quote> errorQuotes = new java.util.HashMap<>();
                            for (String symbol : symbols) {
                                errorQuotes.put(symbol, null);
                            }
                            return errorQuotes;
                        }

                        // Get the quotes array from data.quotes
                        com.fasterxml.jackson.databind.JsonNode quotesArray = dataObject.get("quotes");
                        if (quotesArray == null || !quotesArray.isArray()) {
                            logger.warn(
                                    "Fauxnance batch response has no quotes array at data.quotes: count={}",
                                    symbols.size()
                            );
                            Map<String, Quote> errorQuotes = new java.util.HashMap<>();
                            for (String symbol : symbols) {
                                errorQuotes.put(symbol, null);
                            }
                            return errorQuotes;
                        }

                        Map<String, Quote> quotes = new java.util.HashMap<>();
                        logger.info("Batch response quotes array has {} items", quotesArray.size());
                        
                        for (com.fasterxml.jackson.databind.JsonNode item : quotesArray) {
                            try {
                                // Extract the nested quote object from each item
                                com.fasterxml.jackson.databind.JsonNode nestedQuote = item.get("quote");
                                if (nestedQuote == null) {
                                    logger.warn("Item has no nested quote field: {}", item.toString());
                                    continue;
                                }

                                logger.debug("Attempting to parse batch quote item: {}", nestedQuote.toString());
                                Quote quote = objectMapper.treeToValue(
                                        nestedQuote,
                                        Quote.class
                                );
                                if (quote != null) {
                                    String symbol = quote.getSymbol();
                                    BigDecimal price = quote.getPrice();
                                    logger.info("Parsed quote: symbol={} price={} symbol_is_null={} price_is_null={}", 
                                            symbol, price, symbol == null, price == null);
                                    if (symbol != null) {
                                        quotes.put(symbol, quote);
                                        logger.debug(
                                                "Stored batch quote: symbol={} price={} bid={} ask={}",
                                                symbol,
                                                price,
                                                quote.getBid(),
                                                quote.getAsk()
                                        );
                                    } else {
                                        logger.warn("Parsed quote has null symbol. Quote object: {}", quote.toString());
                                    }
                                } else {
                                    logger.warn("Parsed quote is null for nested quote: {}", nestedQuote.toString());
                                }
                            } catch (Exception e) {
                                logger.warn(
                                        "Failed to parse quote from batch: error={} item={}",
                                        e.getMessage(),
                                        item.toString(),
                                        e
                                );
                            }
                        }

                        // Ensure all requested symbols are in the map
                        for (String symbol : symbols) {
                            quotes.putIfAbsent(symbol, null);
                        }

                        logger.info(
                                "Batch quotes parsed: requested={} parsed={} symbols={}",
                                symbols.size(),
                                quotes.values().stream().filter(q -> q != null).count(),
                                quotes.keySet()
                        );

                        return quotes;

                    } catch (Exception parseException) {
                        logger.error(
                                "Could not parse Fauxnance batch response: count={} error={}",
                                symbols.size(),
                                parseException.getMessage(),
                                parseException
                        );
                        Map<String, Quote> errorQuotes = new java.util.HashMap<>();
                        for (String symbol : symbols) {
                            errorQuotes.put(symbol, null);
                        }
                        return errorQuotes;
                    }
                }

              
                if (status >= 400 && status < 500) {
                    logger.warn(
                            "Fauxnance batch 4xx: count={} status={} body={}",
                            symbols.size(),
                            status,
                            body
                    );
                    Map<String, Quote> errorQuotes = new java.util.HashMap<>();
                    for (String symbol : symbols) {
                        errorQuotes.put(symbol, null);
                    }
                    return errorQuotes;
                }

          
                if (status >= 500) {
                    logger.warn(
                            "Fauxnance batch 5xx: count={} status={} attempt={}/{}",
                            symbols.size(),
                            status,
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
                        "Unexpected Fauxnance batch HTTP status: count={} status={}",
                        symbols.size(),
                        status
                );

                Map<String, Quote> errorQuotes = new java.util.HashMap<>();
                for (String symbol : symbols) {
                    errorQuotes.put(symbol, null);
                }
                return errorQuotes;

            } catch (java.net.http.HttpTimeoutException e) {
                logger.warn(
                        "Batch quote timeout: count={} attempt={}/{} error={}",
                        symbols.size(),
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
                        Map<String, Quote> errorQuotes = new java.util.HashMap<>();
                        for (String symbol : symbols) {
                            errorQuotes.put(symbol, null);
                        }
                        return errorQuotes;
                    }
                }

            } catch (java.io.IOException e) {
                logger.warn(
                        "Batch quote network error: count={} attempt={}/{} error={}",
                        symbols.size(),
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
                        Map<String, Quote> errorQuotes = new java.util.HashMap<>();
                        for (String symbol : symbols) {
                            errorQuotes.put(symbol, null);
                        }
                        return errorQuotes;
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn(
                        "Batch quote request interrupted: count={}",
                        symbols.size()
                );
                Map<String, Quote> errorQuotes = new java.util.HashMap<>();
                for (String symbol : symbols) {
                    errorQuotes.put(symbol, null);
                }
                return errorQuotes;

            } catch (Exception e) {
                logger.error(
                        "Unexpected Fauxnance batch error: count={} error={}",
                        symbols.size(),
                        e.getMessage(),
                        e
                );
                Map<String, Quote> errorQuotes = new java.util.HashMap<>();
                for (String symbol : symbols) {
                    errorQuotes.put(symbol, null);
                }
                return errorQuotes;
            }
        }

        logger.error(
                "Batch quote retry budget exhausted: count={}",
                symbols.size()
        );

        Map<String, Quote> errorQuotes = new java.util.HashMap<>();
        for (String symbol : symbols) {
            errorQuotes.put(symbol, null);
        }
        return errorQuotes;
    }
}