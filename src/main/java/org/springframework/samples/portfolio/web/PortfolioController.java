package org.springframework.samples.portfolio.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.annotation.MessageMapping;
import org.springframework.samples.portfolio.PortfolioPosition;
import org.springframework.samples.portfolio.service.PortfolioService;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.messaging.annotation.SubscribeEvent;


@Controller
public class PortfolioController {

	private static final Log logger = LogFactory.getLog(PortfolioController.class);

	private final PortfolioService portfolioService;

	@Autowired
	public PortfolioController(PortfolioService portfolioService) {
		Assert.notNull(portfolioService, "PortfolioService is required");
		this.portfolioService = portfolioService;
	}

	@SubscribeEvent("/positions")
	public List<PortfolioPosition> getPortfolios() throws IOException {
		// Mock getting the username for now by randomly selecting user or admin
		String username = Math.random() < .5 ? "admin" : "user";
		PortfolioPosition[] positions = portfolioService.findPortfolio(username).getPositions();
		return Arrays.asList(positions);
	}

	@MessageMapping("/tradeRequest")
	public void executeTrade(TradeRequest tradeRequest) {

		logger.debug("Trade request: " + tradeRequest);

		// TODO: execute
	}

}
