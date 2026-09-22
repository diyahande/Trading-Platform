package com.capstone.executor.service;

import com.capstone.executor.event.EventEnvelope;
import com.capstone.executor.event.TradeEventPayload;

// Interface for publishing trade events to trade-events topic
public interface TradeEventPublisher {
    
    // Publish event to trade-events topic; keyed by accountId for per-account ordering
    void publish(EventEnvelope<TradeEventPayload> event);
}
