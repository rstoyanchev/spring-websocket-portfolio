/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.portfolio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Portfolio {

	private final Map<String,PortfolioPosition> positionLookup = new HashMap<String,PortfolioPosition>();


	public List<PortfolioPosition> getPositions() {
		return new ArrayList<PortfolioPosition>(positionLookup.values());
	}

	public void addPosition(PortfolioPosition position) {
		this.positionLookup.put(position.getTicker(), position);
	}

	public PortfolioPosition getPortfolioPosition(String ticker) {
		return this.positionLookup.get(ticker);
	}

	/**
	 * @return the updated position or null
	 */
	public PortfolioPosition buy(String ticker, int sharesToBuy) {
		PortfolioPosition position = this.positionLookup.get(ticker);
		if ((position == null) || (sharesToBuy < 1)) {
			return null;
		}
		position = new PortfolioPosition(position, sharesToBuy);
		this.positionLookup.put(ticker, position);
		return position;
	}

	/**
	 * @return the updated position or null
	 */
	public PortfolioPosition sell(String ticker, int sharesToSell) {
		PortfolioPosition position = this.positionLookup.get(ticker);
		if ((position == null) || (sharesToSell < 1) || (position.getShares() < sharesToSell)) {
			return null;
		}
		position = new PortfolioPosition(position, -sharesToSell);
		this.positionLookup.put(ticker, position);
		return position;
	}

}
