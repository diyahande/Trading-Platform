package com.capstone.service;

import com.capstone.exception.MarketDataUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FauxnanceBatchQuoteService {

    private static final int MAX_SYMBOLS_PER_REQUEST = 25;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${fauxnance.base-url:}")
    private String baseUrl;

    @Value("${fauxnance.api-key:}")
    private String apiKey;

    public Map<String, QuoteResult> getQuotes(List<String> symbols) {
        List<String> normalized = symbols == null
                ? List.of()
                : symbols.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(this::normalizeSymbol)
                .distinct()
                .toList();

        if (normalized.isEmpty()) {
            return Map.of();
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new MarketDataUnavailableException("Pricing unavailable");
        }

        Map<String, QuoteResult> collected = new LinkedHashMap<>();
        for (String symbol : normalized) {
            collected.put(symbol, QuoteResult.unavailable());
        }

        for (int i = 0; i < normalized.size(); i += MAX_SYMBOLS_PER_REQUEST) {
            List<String> batch = normalized.subList(i, Math.min(i + MAX_SYMBOLS_PER_REQUEST, normalized.size()));
            Map<String, QuoteResult> batchQuotes = fetchBatchSafely(batch);
            batchQuotes.forEach(collected::put);
        }

        return collected;
    }

    public Integer getQuotaRemaining() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }

        try {
            String url = normalizedBaseUrl() + "/usage";
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/json")
                    .GET();

            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("X-Api-Key", apiKey);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            Integer remaining = firstInt(root,
                    "quotaRemaining",
                    "remaining",
                    "dailyQuotaRemaining",
                    "remainingQuota");
            if (remaining != null) {
                return remaining;
            }

            JsonNode data = root.get("data");
            if (data != null && data.isObject()) {
                return firstInt(data,
                        "quotaRemaining",
                        "remaining",
                        "dailyQuotaRemaining",
                        "remainingQuota");
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

    private Map<String, QuoteResult> fetchBatchSafely(List<String> batch) {
        try {
            return fetchBatch(batch);
        } catch (Exception ex) {
            Map<String, QuoteResult> unavailable = new HashMap<>();
            for (String symbol : batch) {
                unavailable.put(symbol, QuoteResult.unavailable());
            }
            return unavailable;
        }
    }

    private Map<String, QuoteResult> fetchBatch(List<String> batch) throws Exception {
        Map<String, QuoteResult> result = new HashMap<>();
        for (String symbol : batch) {
            result.put(symbol, QuoteResult.unavailable());
        }

        String joined = batch.stream().collect(Collectors.joining(","));
        String query = URLEncoder.encode(joined, StandardCharsets.UTF_8);
        String url = normalizedBaseUrl() + "/quotes?symbols=" + query;

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
            return result;
        }

        JsonNode root = objectMapper.readTree(response.body());
        for (JsonNode quoteNode : extractQuoteNodes(root)) {
            JsonNode quotePayload = extractQuotePayload(quoteNode);

            String symbol = extractSymbol(quoteNode, quotePayload).map(this::normalizeSymbol).orElse(null);
            if (symbol == null || !result.containsKey(symbol)) {
                continue;
            }

            BigDecimal price = firstDecimal(quotePayload, "lastPrice", "price", "ltp", "last");
            String currency = firstText(quotePayload, "currency", "quoteCurrency").orElse(null);
            OffsetDateTime asOf = firstDateTime(quotePayload,
                    "priceAsOf",
                    "asOf",
                    "timestamp",
                    "observedAt",
                    "updatedAt").orElse(null);
            boolean stale = firstBoolean(quoteNode, "stale")
                    .or(() -> firstBoolean(quotePayload, "stale"))
                    .orElse(false);

            if (price != null) {
                result.put(symbol, new QuoteResult(price, currency, asOf, stale));
            }
        }

        return result;
    }

    private List<JsonNode> extractQuoteNodes(JsonNode root) {
        List<JsonNode> nodes = new ArrayList<>();
        if (root == null || root.isNull()) {
            return nodes;
        }

        if (root.isArray()) {
            root.forEach(nodes::add);
            return nodes;
        }

        JsonNode data = root.get("data");
        if (data != null) {
            if (data.isArray()) {
                data.forEach(nodes::add);
                return nodes;
            }
            if (data.isObject()) {
                if (data.has("quotes") && data.get("quotes").isArray()) {
                    data.get("quotes").forEach(nodes::add);
                    return nodes;
                }
                if (data.has("symbol")) {
                    nodes.add(data);
                    return nodes;
                }
                data.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isObject()) {
                        nodes.add(withSymbol(entry.getValue(), entry.getKey()));
                    }
                });
                return nodes;
            }
        }

        if (root.has("quotes") && root.get("quotes").isArray()) {
            root.get("quotes").forEach(nodes::add);
            return nodes;
        }

        root.fields().forEachRemaining(entry -> {
            if (entry.getValue().isObject()) {
                nodes.add(withSymbol(entry.getValue(), entry.getKey()));
            }
        });

        return nodes;
    }

    private JsonNode withSymbol(JsonNode node, String symbol) {
        if (node.has("symbol")) {
            return node;
        }
        return ((com.fasterxml.jackson.databind.node.ObjectNode) node.deepCopy()).put("symbol", symbol);
    }

    private String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizedBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private Optional<String> extractSymbol(JsonNode wrapperNode, JsonNode quotePayload) {
        Optional<String> symbol = firstText(wrapperNode, "symbol", "ticker");
        if (symbol.isPresent()) {
            return symbol;
        }
        return firstText(quotePayload, "symbol", "ticker");
    }

    private JsonNode extractQuotePayload(JsonNode node) {
        JsonNode quote = node.get("quote");
        if (quote != null && quote.isObject()) {
            return quote;
        }
        return node;
    }

    private Optional<String> firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.get(key);
            if (child != null && !child.isNull() && child.isTextual()) {
                return Optional.of(child.asText());
            }
        }
        return Optional.empty();
    }

    private Optional<Boolean> firstBoolean(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.get(key);
            if (child != null && !child.isNull() && child.isBoolean()) {
                return Optional.of(child.asBoolean());
            }
        }
        return Optional.empty();
    }

    private Integer firstInt(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.get(key);
            if (child != null && !child.isNull() && child.canConvertToInt()) {
                return child.asInt();
            }
        }
        return null;
    }

    private BigDecimal firstDecimal(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.get(key);
            if (child == null || child.isNull()) {
                continue;
            }
            if (child.isNumber()) {
                return child.decimalValue();
            }
            if (child.isTextual()) {
                try {
                    return new BigDecimal(child.asText());
                } catch (NumberFormatException ignored) {
                    // Try next field.
                }
            }
        }
        return null;
    }

    private Optional<OffsetDateTime> firstDateTime(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.get(key);
            if (child == null || child.isNull() || !child.isTextual()) {
                continue;
            }
            try {
                return Optional.of(OffsetDateTime.parse(child.asText()));
            } catch (Exception ignored) {
                // Try next field.
            }
        }
        return Optional.empty();
    }

    public record QuoteResult(
            BigDecimal lastPrice,
            String currency,
            OffsetDateTime asOf,
            boolean stale
    ) {
        public static QuoteResult unavailable() {
            return new QuoteResult(null, null, null, true);
        }

        public boolean hasPrice() {
            return lastPrice != null;
        }
    }
}
