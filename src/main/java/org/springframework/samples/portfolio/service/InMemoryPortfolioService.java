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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.core.MessageSendingOperations;
import org.springframework.samples.portfolio.Portfolio;
import org.springframework.samples.portfolio.PortfolioPosition;
import org.springframework.samples.portfolio.web.TradeRequest;
import org.springframework.samples.portfolio.web.TradeRequest.TradeRequestAction;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * @author Rob Winch
 */
@Service
public class InMemoryPortfolioService implements PortfolioService {

	private final MessageSendingOperations<String> messagingTemplate;

	private final Map<String, Portfolio> usernameToPortfolio = new HashMap<>();


	@Autowired
	public InMemoryPortfolioService(MessageSendingOperations<String> messagingTemplate) {

		this.messagingTemplate = messagingTemplate;

		Portfolio portfolio = new Portfolio();
		portfolio.addPosition(new PortfolioPosition("Citrix Systems, Inc.", "CTXS", 24.30, 75));
		portfolio.addPosition(new PortfolioPosition("Dell Inc.", "DELL", 13.44, 50));
		portfolio.addPosition(new PortfolioPosition("Microsoft", "MSFT", 34.15, 33));
		this.usernameToPortfolio.put("fabrice", portfolio);

		portfolio = new Portfolio();
		portfolio.addPosition(new PortfolioPosition("EMC Corporation", "EMC", 24.30, 75));
		portfolio.addPosition(new PortfolioPosition("Google Inc", "GOOG", 905.09, 5));
		portfolio.addPosition(new PortfolioPosition("VMware, Inc.", "VMW", 65.58, 23));
		this.usernameToPortfolio.put("paulson", portfolio);
	}

	@Override
	public Portfolio findPortfolio(String username) {
		return this.usernameToPortfolio.get(username);
	}

	@Override
	public void executeTradeRequest(TradeRequest tradeRequest, String username) {
		Assert.notNull(username, "username is required");

		Portfolio portfolio = findPortfolio(username);
		PortfolioPosition position = portfolio.getPortfolioPosition(tradeRequest.getTicker());
		int shares = position.getShares();

		if(tradeRequest.getAction() == TradeRequestAction.Buy) {
			shares += tradeRequest.getShares();
		}
		else {
			// TODO validation
			shares -= tradeRequest.getShares();
		}
		PortfolioPosition newPosition = new PortfolioPosition(position, shares);
		portfolio.addPosition(newPosition);

		this.messagingTemplate.convertAndSend("/user/" + username + "/queue/trade-confirmation", newPosition);
	}

}
