package com.capstone.service.signal;

import com.capstone.dto.CandleDataResponse;
import com.capstone.exception.MarketDataUnavailableException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FauxnanceClient {

    private final RestTemplate restTemplate;

    @Value("${fauxnance.base-url:}")
    private String fauxnanceBaseUrl;

    @Value("${fauxnance.api-key:}")
    private String fauxnanceApiKey;

    public List<CandleDataResponse> fetchDailyCandles(String symbol) {
        if (fauxnanceBaseUrl == null || fauxnanceBaseUrl.isBlank()) {
            throw new MarketDataUnavailableException("Fauxnance base URL is not configured");
        }

        String sanitizedBaseUrl = fauxnanceBaseUrl.endsWith("/")
                ? fauxnanceBaseUrl.substring(0, fauxnanceBaseUrl.length() - 1)
                : fauxnanceBaseUrl;

        UriComponentsBuilder uriBuilder = UriComponentsBuilder
            .fromUriString(sanitizedBaseUrl)
                .pathSegment("candles", symbol)
                .queryParam("interval", "1d")
                .queryParam("range", "200d");

        String url = uriBuilder.toUriString();
        try {
            HttpHeaders headers = new HttpHeaders();
            if (fauxnanceApiKey != null && !fauxnanceApiKey.isBlank()) {
                headers.set("X-Api-Key", fauxnanceApiKey);
            }

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<CandleEnvelope> response =
                    restTemplate.exchange(url, HttpMethod.GET, request, CandleEnvelope.class);

            CandleEnvelope envelope = response.getBody();
            if (envelope == null || envelope.getData() == null || envelope.getData().getCandles() == null) {
                log.warn("Fauxnance returned empty candle payload for symbol={}", symbol);
                return Collections.emptyList();
            }
            return envelope.getData().getCandles();
        } catch (HttpStatusCodeException ex) {
            log.error(
                    "Fauxnance request failed for symbol={}, status={}, body={}, url={}",
                    symbol,
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString(),
                    url
            );
            throw new MarketDataUnavailableException("Failed to fetch candles from Fauxnance: HTTP " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            log.error("Fauxnance request failed for symbol={}, url={}, message={}", symbol, url, ex.getMessage());
            throw new MarketDataUnavailableException("Failed to fetch candles from Fauxnance");
        }
    }

    @Data
    private static class CandleEnvelope {
        private CandlePayload data;
    }

    @Data
    private static class CandlePayload {
        private String symbol;
        private String interval;
        private String currency;
        private List<CandleDataResponse> candles;
    }
}
