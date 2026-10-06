package com.capstone.executor.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

// Generic envelope for all Kafka events
public class EventEnvelope<T> {
    @JsonProperty("eventId")
    private String eventId;
    @JsonProperty("eventType")
    private String eventType;
    @JsonProperty("eventTime")
    private Instant eventTime;
    @JsonProperty("source")
    private String source;
    @JsonProperty("schemaVersion")
    private int schemaVersion;
    @JsonProperty("payload")
    private T payload;
    
    public EventEnvelope() {}
    public EventEnvelope(String eventId, String eventType, Instant eventTime, String source, int schemaVersion, T payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.source = source;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
    }
    
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }
}
