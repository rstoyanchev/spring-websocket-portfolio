package org.springframework.samples.portfolio;

import java.util.HashMap;
import java.util.Map;


public class Portfolio {

	private final Map<String,PortfolioPosition> tickerToPortfolioPosition = new HashMap<String,PortfolioPosition>();

	public PortfolioPosition[] getPositions() {
		return this.tickerToPortfolioPosition.values().toArray(new PortfolioPosition[this.tickerToPortfolioPosition.size()]);
	}

	public void addPosition(PortfolioPosition position) {
		this.tickerToPortfolioPosition.put(position.getTicker(),position);
	}

	public PortfolioPosition getPortfolioPosition(String ticker) {
		return tickerToPortfolioPosition.get(ticker);
	}
}
