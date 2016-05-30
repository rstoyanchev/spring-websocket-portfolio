package org.springframework.samples.portfolio.service;

import org.springframework.samples.portfolio.PortfolioPosition;

/**
 * Created by achmad on 30/05/16.
 */
public class TradeResult {

    private final String user;
    private final PortfolioPosition position;
    private final long timestamp;

    public TradeResult(String user, PortfolioPosition position) {
        this.user = user;
        this.position = position;
        this.timestamp = System.currentTimeMillis();
    }

    public String getUser() {
        return user;
    }

    public PortfolioPosition getPosition() {
        return position;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
