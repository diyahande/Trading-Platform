package com.capstone.service;

import com.capstone.exception.MarketDataUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class MarketQuoteService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${fauxnance.base-url:}")
    private String baseUrl;

    @Value("${fauxnance.api-key:}")
    private String apiKey;

    public QuoteSnapshot getQuote(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new MarketDataUnavailableException("Market quote symbol is missing");
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new MarketDataUnavailableException("Market data base URL is not configured");
        }

        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String url = normalizedBaseUrl + "/quotes/" + symbol;

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET();

            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("X-Api-Key", apiKey);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MarketDataUnavailableException(
                        "Market quote unavailable for symbol %s (status %d)".formatted(symbol, response.statusCode())
                );
            }

            QuoteEnvelope envelope = objectMapper.readValue(response.body(), QuoteEnvelope.class);
            if (envelope == null || envelope.data == null || envelope.data.price == null) {
                throw new MarketDataUnavailableException("Current market price is unavailable for symbol %s".formatted(symbol));
            }

            return new QuoteSnapshot(
                    envelope.data.symbol,
                    envelope.data.price,
                    envelope.data.previousClose,
                    envelope.data.currency
            );
        } catch (MarketDataUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MarketDataUnavailableException(
                    "Market quote request failed for symbol %s: %s".formatted(symbol, ex.getMessage())
            );
        }
    }

    public record QuoteSnapshot(
            String symbol,
            BigDecimal price,
            BigDecimal previousClose,
            String currency
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class QuoteEnvelope {
        @JsonProperty("data")
        private QuotePayload data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class QuotePayload {
        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("price")
        private BigDecimal price;

        @JsonProperty("previousClose")
        private BigDecimal previousClose;

        @JsonProperty("currency")
        private String currency;
    }
}