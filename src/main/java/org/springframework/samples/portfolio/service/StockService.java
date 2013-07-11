package org.springframework.samples.portfolio.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.messaging.support.WebMessagingTemplate;


@Component
public class StockService {

	private final WebMessagingTemplate messagingTemplate;

	private final StockQuoteGenerator quoteGenerator = new StockQuoteGenerator();


	@Autowired
	public StockService(WebMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}


	@Scheduled(fixedDelay=2000)
	public void sendQuotes() {
		Quote quote = this.quoteGenerator.nextQuote();
		this.messagingTemplate.convertAndSend("/topic/PRICE.STOCK.NASDAQ." + quote.getTicker(), quote);
	}

}
