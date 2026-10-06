package com.capstone.executor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class FxConversionService {

    private static final String FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/v2/rates";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FxConversionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }

        String from = normalizeCurrency(fromCurrency);
        String to = normalizeCurrency(toCurrency);
        if (from.equals(to)) {
            return amount;
        }

        BigDecimal fromPerToRate = fetchRate(to, from);
        if (fromPerToRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("FX rate must be positive");
        }

        return amount.divide(fromPerToRate, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal fetchRate(String baseCurrency, String quoteCurrency) {
        try {
            String endpoint = FRANKFURTER_BASE_URL + "?base=" + URLEncoder.encode(baseCurrency, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Failed to fetch FX rates: HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            BigDecimal rate = readRate(root, quoteCurrency);
            if (rate == null) {
                throw new IllegalStateException("FX rate not found for " + baseCurrency + " -> " + quoteCurrency);
            }
            return rate;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to fetch FX conversion", ex);
        }
    }

    private BigDecimal readRate(JsonNode root, String quoteCurrency) {
        if (root == null || root.isNull()) {
            return null;
        }

        JsonNode ratesNode = root.get("rates");
        if (ratesNode != null && ratesNode.isObject()) {
            JsonNode direct = ratesNode.get(quoteCurrency);
            if (direct != null && direct.isNumber()) {
                return direct.decimalValue();
            }
        }

        if (root.isArray()) {
            for (JsonNode node : root) {
                JsonNode quote = node.get("quote");
                JsonNode rate = node.get("rate");
                if (quote != null && rate != null && quoteCurrency.equalsIgnoreCase(quote.asText()) && rate.isNumber()) {
                    return rate.decimalValue();
                }
            }
        }

        return null;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        return currency.trim().toUpperCase();
    }
}
