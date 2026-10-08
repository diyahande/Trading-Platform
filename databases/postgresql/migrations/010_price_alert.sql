-- Price alerts for watchlist items
-- Threshold-based alerts that trigger when market data crosses the specified price
CREATE TABLE IF NOT EXISTS price_alert (
    alert_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    
    watchlist_id BIGINT NOT NULL,
    
    -- Threshold price at which the alert triggers
    threshold NUMERIC(19, 4) NOT NULL,
    
    -- ABOVE: triggers when price rises through threshold
    -- BELOW: triggers when price falls through threshold
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('ABOVE', 'BELOW')),
    
    -- ACTIVE: alert is being evaluated
    -- TRIGGERED: threshold crossed, alert has fired
    -- DEACTIVATED: customer manually deactivated
    -- DELIVERY_FAILED: notification delivery attempt failed
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    triggered_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_price_alert_watchlist
        FOREIGN KEY (watchlist_id)
        REFERENCES watchlist(watchlist_id)
        ON DELETE CASCADE,
    
    CONSTRAINT chk_threshold_positive
        CHECK (threshold > 0)
);

-- Index for evaluating alerts per symbol during market data consumption
-- Query: find active alerts for symbols in incoming market data
CREATE INDEX idx_price_alert_status_on_watchlist
    ON price_alert(status, watchlist_id)
    WHERE status = 'ACTIVE';
