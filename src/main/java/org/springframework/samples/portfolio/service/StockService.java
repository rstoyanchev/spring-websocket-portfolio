package org.springframework.samples.portfolio.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
public class StockService {

	private static Log logger = LogFactory.getLog(StockService.class);

	private final SimpMessagingTemplate messagingTemplate;

	private final StockQuoteGenerator quoteGenerator = new StockQuoteGenerator();


	@Autowired
	public StockService(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@Scheduled(fixedDelay=2000)
	public void sendQuotes() {
		Quote quote = this.quoteGenerator.nextQuote();
		if (logger.isTraceEnabled()) {
			logger.trace("Sending quote " + quote);
		}
		this.messagingTemplate.convertAndSend("/topic/stocks.PRICE.STOCK.NASDAQ." + quote.getTicker(), quote);
	}

}
