package org.springframework.samples.portfolio.web;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SubscribeEvent;
import org.springframework.samples.portfolio.Portfolio;
import org.springframework.samples.portfolio.PortfolioPosition;
import org.springframework.samples.portfolio.service.PortfolioService;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;


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
	public List<PortfolioPosition> getPortfolios(Principal principal) throws IOException {

		Portfolio portfolio = this.portfolioService.findPortfolio(principal.getName());
		if (portfolio == null) {
			Assert.notNull(portfolio, "Portfolio not found: " + principal);
			return null;
		}

		logger.debug("Returning portfolio for " + principal.getName());
		return Arrays.asList(portfolio.getPositions());
	}

	@MessageMapping("/trade")
	public void executeTrade(TradeRequest tradeRequest, Principal principal) {
		logger.debug("Trade: " + tradeRequest);
		this.portfolioService.executeTradeRequest(tradeRequest, principal.getName());
	}

}
