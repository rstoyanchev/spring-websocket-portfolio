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
package org.springframework.samples.portfolio.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.samples.portfolio.Portfolio;
import org.springframework.samples.portfolio.PortfolioPosition;
import org.springframework.samples.portfolio.web.TradeRequest;
import org.springframework.samples.portfolio.web.TradeRequest.TradeRequestAction;

/**
 * @author Rob Winch
 *
 */
public class PortfolioService {
	private final Map<String, Portfolio> usernameToPortfolio = new HashMap<>();

	public synchronized void setPortfolio(String username, Portfolio portfolio) {
		this.usernameToPortfolio.put(username, portfolio);
	}

	public synchronized Portfolio findPortfolio(String username) {
		return usernameToPortfolio.get(username);
	}

	public synchronized void trade(String username, TradeRequest tradeRequest) {
		String ticker = tradeRequest.getTicker();
		// TODO we cannot know the username yet, so remove this once we do know it
		if(username == null) {
			username = guessUsername(ticker);
		}
		Portfolio portfolio = findPortfolio(username);
		PortfolioPosition portfolioPosition = portfolio.getPortfolioPosition(ticker);
		int userShares = portfolioPosition.getShares();
		if(tradeRequest.getAction() == TradeRequestAction.Buy) {
			userShares += tradeRequest.getShares();
		} else {
			// TODO validation
			userShares -= tradeRequest.getShares();
		}
		PortfolioPosition newPortfolioPosition = 
				new PortfolioPosition(portfolioPosition.getCompany(), portfolioPosition.getTicker(), portfolioPosition.getPrice(), userShares);
		portfolio.addPosition(newPortfolioPosition);

		// TODO send response to the client
	}

	private String guessUsername(String ticker) {
		for(Map.Entry<String, Portfolio> entry : usernameToPortfolio.entrySet()) {
			if(entry.getValue().getPortfolioPosition(ticker) != null) {
				return entry.getKey();
			}
		}
		return null;
	}
}
