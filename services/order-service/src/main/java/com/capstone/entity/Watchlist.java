package com.capstone.entity;

import java.time.Instant;

public class Watchlist {

    private final Long watchlistId;
    private final Long userId;
    private final Long instrumentId;
    private final String name;
    private final Instant createdAt;

    public Watchlist(
            Long watchlistId,
            Long userId,
            Long instrumentId,
            String name,
            Instant createdAt) {

        this.watchlistId = watchlistId;
        this.userId = userId;
        this.instrumentId = instrumentId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getWatchlistId() {
        return watchlistId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
