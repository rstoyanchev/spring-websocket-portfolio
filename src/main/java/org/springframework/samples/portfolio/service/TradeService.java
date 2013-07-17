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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.samples.portfolio.Portfolio;
import org.springframework.samples.portfolio.PortfolioPosition;
import org.springframework.samples.portfolio.service.Trade.TradeAction;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Service
public class TradeService {

	private static final Log logger = LogFactory.getLog(TradeService.class);

	private final SimpMessageSendingOperations messagingTemplate;

	private final PortfolioService portfolioService;

	private final List<TradeResult> tradeResults = new CopyOnWriteArrayList<>();


	@Autowired
	public TradeService(
			@Qualifier("inboundMessagingTemplate") SimpMessageSendingOperations messagingTemplate,
			PortfolioService portfolioService) {

		this.messagingTemplate = messagingTemplate;
		this.portfolioService = portfolioService;
	}

	/**
	 * In real application a trade is probably executed in an external system, i.e. asynchronously.
	 */
	public void executeTrade(Trade trade) {

		Portfolio portfolio = this.portfolioService.findPortfolio(trade.getUsername());
		String ticker = trade.getTicker();
		int sharesToTrade = trade.getShares();

		PortfolioPosition newPosition = (trade.getAction() == TradeAction.Buy) ?
				portfolio.buy(ticker, sharesToTrade) : portfolio.sell(ticker, sharesToTrade);

		this.tradeResults.add(new TradeResult(trade, newPosition));
	}

	@Scheduled(fixedDelay=3000)
	public void sendTradeNotifications() {

		for (TradeResult tr : this.tradeResults) {
			if (System.currentTimeMillis() >= (tr.createTime + 3000)) {
				String user = tr.trade.getUsername();
				if (tr.position != null) {
					logger.debug("Position update: " + tr.position);
					this.messagingTemplate.convertAndSendToUser(user, "/queue/position-updates", tr.position);
				}
				else {
					logger.debug("Rejected trade: " + tr.trade);
					this.messagingTemplate.convertAndSendToUser(user, "/queue/rejected-trades", tr.trade);
				}
				this.tradeResults.remove(tr);
			}
		}
	}


	private static class TradeResult {

		private final Trade trade;
		private final PortfolioPosition position;
		private final long createTime;

		public TradeResult(Trade trade, PortfolioPosition position) {
			this.trade = trade;
			this.position = position;
			this.createTime = System.currentTimeMillis();
		}
	}

}
