package com.capstone.watchlist;

import com.capstone.entity.Account;
import com.capstone.service.AccountService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthPriceAlertNotificationClient implements AlertNotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(AuthPriceAlertNotificationClient.class);

    private final ObjectMapper objectMapper;
    private final AccountService accountService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${auth.notifications.base-url:http://localhost:3000}")
    private String authServiceBaseUrl;

    @Value("${auth.notifications.price-alert-path:/auth/notifications/price-alert}")
    private String priceAlertPath;

    @Value("${auth.notifications.internal-token:}")
    private String internalToken;

    @Override
    public void deliverPriceAlert(
            Long alertId,
            Long userId,
            String symbol,
            BigDecimal currentPrice,
            BigDecimal threshold,
            String direction
    ) throws AlertDeliveryException {
        if (authServiceBaseUrl == null || authServiceBaseUrl.isBlank()) {
            throw new AlertDeliveryException("auth.notifications.base-url is not configured");
        }

        if (internalToken == null || internalToken.isBlank()) {
            throw new AlertDeliveryException("auth.notifications.internal-token is not configured");
        }

        List<Account> candidateAccounts;
        try {
            candidateAccounts = new ArrayList<>(accountService.getActiveAccountsByUserId(userId));
        } catch (Exception ex) {
            throw new AlertDeliveryException("Could not find active accounts for userId=" + userId, ex);
        }

        if (candidateAccounts.isEmpty()) {
            throw new AlertDeliveryException("No active account found for userId=" + userId);
        }

        String endpoint = normalizeBase(authServiceBaseUrl) + normalizePriceAlertPath(priceAlertPath);
        AlertDeliveryException lastFailure = null;

        for (Account account : candidateAccounts) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("alertId", alertId);
            payload.put("accountReference", account.getAccountReference());
            payload.put("symbol", symbol);
            payload.put("currentPrice", currentPrice.setScale(4, RoundingMode.HALF_UP));
            payload.put("threshold", threshold.setScale(4, RoundingMode.HALF_UP));
            payload.put("direction", direction);
            payload.put("triggeredAt", Instant.now().toString());

            try {
                String requestBody = objectMapper.writeValueAsString(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(2))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("X-Internal-Token", internalToken)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new AlertDeliveryException(
                            "auth-service rejected alert notification: status="
                                    + response.statusCode()
                                    + " body="
                                    + response.body()
                    );
                }

                // Treat 2xx as success by default. Fail only when emailSent is explicitly false.
                boolean emailSentExplicitlyFalse = false;
                String responseBody = response.body();
                if (responseBody != null && !responseBody.isBlank()) {
                    try {
                        PriceAlertNotificationResponse parsedResponse = objectMapper.readValue(
                                responseBody,
                                PriceAlertNotificationResponse.class
                        );
                        if (parsedResponse.emailSent != null && !parsedResponse.emailSent) {
                            emailSentExplicitlyFalse = true;
                        }
                    } catch (Exception parseEx) {
                        JsonNode root = objectMapper.readTree(responseBody);
                        if (root != null && root.has("emailSent") && !root.get("emailSent").asBoolean(true)) {
                            emailSentExplicitlyFalse = true;
                        }
                    }
                }

                if (emailSentExplicitlyFalse) {
                    throw new AlertDeliveryException(
                            "auth-service accepted request but email delivery failed for alertId=" + alertId
                    );
                }

                logger.info(
                        "Price alert notification delivered via auth-service: accountReference={} symbol={} status={}",
                        account.getAccountReference(),
                        symbol,
                        response.statusCode()
                );
                return;
            } catch (AlertDeliveryException ex) {
                lastFailure = ex;
                logger.warn(
                        "Price alert notification attempt failed for accountReference={} alertId={} reason={}",
                        account.getAccountReference(),
                        alertId,
                        ex.getMessage()
                );
            } catch (Exception ex) {
                lastFailure = new AlertDeliveryException("auth-service price alert request failed", ex);
                logger.warn(
                        "Price alert notification request error for accountReference={} alertId={} reason={}",
                        account.getAccountReference(),
                        alertId,
                        ex.getMessage()
                );
            }
        }

        throw lastFailure == null
                ? new AlertDeliveryException("Price alert notification failed for all candidate accounts")
                : lastFailure;
    }

    private String normalizeBase(String baseUrl) {
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    private String normalizePriceAlertPath(String configuredPath) {
        String raw = (configuredPath == null || configuredPath.isBlank())
                ? "/auth/notifications/price-alert"
                : configuredPath.trim();

        String path = raw.replace('\\', '/');

        int authIndex = path.indexOf("/auth/notifications/price-alert");
        if (authIndex >= 0) {
            return path.substring(authIndex);
        }

        authIndex = path.indexOf("auth/notifications/price-alert");
        if (authIndex >= 0) {
            return "/" + path.substring(authIndex);
        }

        return path.startsWith("/") ? path : "/" + path;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PriceAlertNotificationResponse {
        @JsonProperty("alertId")
        public Long alertId;

        @JsonProperty("accountReference")
        public String accountReference;

        @JsonProperty("recipient")
        public String recipient;

        @JsonProperty("emailSent")
        public Boolean emailSent;
    }
}
