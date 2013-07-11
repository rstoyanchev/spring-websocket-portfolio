package org.springframework.samples.portfolio;

import java.util.ArrayList;
import java.util.List;


public class Portfolio {

	private final List<PortfolioPosition> positions = new ArrayList<PortfolioPosition>();


	public PortfolioPosition[] getPositions() {
		return this.positions.toArray(new PortfolioPosition[this.positions.size()]);
	}

	public void addPosition(PortfolioPosition position) {
		this.positions.add(position);
	}

}
