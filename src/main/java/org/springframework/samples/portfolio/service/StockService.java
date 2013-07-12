package org.springframework.samples.portfolio.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
public class StockService {

	private final SimpMessagingTemplate messagingTemplate;

	private final StockQuoteGenerator quoteGenerator = new StockQuoteGenerator();


	@Autowired
	public StockService(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}


	@Scheduled(fixedDelay=2000)
	public void sendQuotes() {
		Quote quote = this.quoteGenerator.nextQuote();
		this.messagingTemplate.convertAndSend("/topic/PRICE.STOCK.NASDAQ." + quote.getTicker(), quote);
	}

}
