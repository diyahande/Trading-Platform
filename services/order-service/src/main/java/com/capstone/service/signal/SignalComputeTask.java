package com.capstone.service.signal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class SignalComputeTask {
    
    private final SignalService signalService;
    
    @Value("${app.signals.watched-symbols:AAPL,MSFT,GOOGL,AMZN,TSLA,INFY,TCS,RELIANCE,SPY,QQQ}")
    private String watchedSymbolsConfig;
    
    private Set<String> watchedSymbols;
    
    private Set<String> getWatchedSymbols() {
        if (watchedSymbols == null) {
            watchedSymbols = new HashSet<>(
                Arrays.asList(watchedSymbolsConfig.split(","))
            );
            watchedSymbols = watchedSymbols.stream()
                .map(String::strip)
                .collect(java.util.stream.Collectors.toSet());
            log.info("Watched symbols configured: {}", watchedSymbols);
        }
        return watchedSymbols;
    }
    
    @Scheduled(fixedRate = 300000)
    public void computeSignalsForWatchedSymbols() {
        log.debug("Signal compute task triggered");
        
        Set<String> symbols = getWatchedSymbols();
        long startTime = System.currentTimeMillis();
        
        for (String symbol : symbols) {
            try {
                signalService.computeAndCacheSignal(symbol);
            } catch (Exception e) {
                log.error("Error computing signal for symbol={}: {}", symbol, e.getMessage(), e);
            }
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Signal compute task completed for {} symbols in {}ms", symbols.size(), elapsed);
    }
    
    public String getTaskStatus() {
        Set<String> symbols = getWatchedSymbols();
        return String.format("Signal compute task active. Monitoring %d symbols: %s", 
            symbols.size(), symbols);
    }
}
