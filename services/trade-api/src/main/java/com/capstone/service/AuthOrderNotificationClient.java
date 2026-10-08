package com.capstone.service;

import com.capstone.entity.Account;
import com.capstone.entity.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthOrderNotificationClient {

    private static final Logger logger = LoggerFactory.getLogger(AuthOrderNotificationClient.class);

    private final ObjectMapper objectMapper;
    private final CustomerPreferenceService customerPreferenceService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${auth.notifications.base-url:http://localhost:3000}")
    private String authServiceBaseUrl;

    @Value("${auth.notifications.order-placed-path:/auth/notifications/order-placed}")
    private String orderPlacedPath;

    @Value("${auth.notifications.internal-token:}")
    private String internalToken;

    public AuthOrderNotificationClient(
            ObjectMapper objectMapper,
            @Lazy CustomerPreferenceService customerPreferenceService) {
        this.objectMapper = objectMapper;
        this.customerPreferenceService = customerPreferenceService;
    }

    public void notifyOrderPlaced(Account account, Order order, String symbol) {
        // Check user's email notification preference — skip if disabled
        boolean emailEnabled = customerPreferenceService.isEmailNotificationsEnabled(account.getAccountReference());
        if (!emailEnabled) {
            logger.info(
                    "Order notification skipped: email notifications disabled by user preference for accountReference={} orderId={}",
                    account.getAccountReference(),
                    order.getOrderId()
            );
            return;
        }

        if (authServiceBaseUrl == null || authServiceBaseUrl.isBlank()) {
            logger.warn("Order notification skipped: auth.notifications.base-url is not configured");
            return;
        }

        if (internalToken == null || internalToken.isBlank()) {
            logger.warn(
                    "Order notification skipped: auth.notifications.internal-token is not configured for orderId={}",
                    order.getOrderId()
            );
            return;
        }

        String normalizedBase = authServiceBaseUrl.endsWith("/")
                ? authServiceBaseUrl.substring(0, authServiceBaseUrl.length() - 1)
                : authServiceBaseUrl;

        String normalizedPath = normalizeOrderPlacedPath(orderPlacedPath);

        String endpoint = normalizedBase + normalizedPath;

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getOrderId().toString());
        payload.put("accountReference", account.getAccountReference());
        payload.put("symbol", symbol);
        payload.put("side", order.getSide().name());
        payload.put("quantity", order.getQuantity().intValue());
        payload.put("limitPrice", order.getPrice().setScale(4, RoundingMode.HALF_UP));
        payload.put("currency", account.getCurrency());
        payload.put("status", order.getStatus().name());
        payload.put("createdOn", order.getCreatedAt().toString());

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
                logger.warn(
                        "Order notification failed: orderId={} status={} body={}",
                        order.getOrderId(),
                        response.statusCode(),
                        response.body()
                );
                return;
            }

            logger.info(
                    "Order notification delivered to auth-service: orderId={} accountReference={} status={}",
                    order.getOrderId(),
                    account.getAccountReference(),
                    response.statusCode()
            );
        } catch (Exception ex) {
            logger.warn(
                    "Order notification request error: orderId={} reason={}",
                    order.getOrderId(),
                    ex.getMessage()
            );
        }
    }

    private String normalizeOrderPlacedPath(String configuredPath) {
        String raw = (configuredPath == null || configuredPath.isBlank())
                ? "/auth/notifications/order-placed"
                : configuredPath.trim();

        String path = raw.replace('\\', '/');

        // Recover from malformed values such as "C:/Program Files/Git/auth/..."
        // that can appear when paths are shell-transformed in Git Bash.
        int authIndex = path.indexOf("/auth/notifications/order-placed");
        if (authIndex >= 0) {
            if (authIndex > 0) {
                logger.warn(
                        "Normalized auth notification path from malformed value: configuredPath={} normalizedPath={}",
                        configuredPath,
                        path.substring(authIndex)
                );
            }
            return path.substring(authIndex);
        }

        authIndex = path.indexOf("auth/notifications/order-placed");
        if (authIndex >= 0) {
            String normalized = "/" + path.substring(authIndex);
            if (!normalized.equals(path)) {
                logger.warn(
                        "Normalized auth notification path from malformed value: configuredPath={} normalizedPath={}",
                        configuredPath,
                        normalized
                );
            }
            return normalized;
        }

        return path.startsWith("/") ? path : "/" + path;
    }
}
