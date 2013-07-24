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

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.core.MessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Service
public class QuoteService {

	private static Log logger = LogFactory.getLog(QuoteService.class);

	private final MessageSendingOperations<String> messagingTemplate;

	private final StockQuoteGenerator quoteGenerator = new StockQuoteGenerator();


	@Autowired
	public QuoteService(MessageSendingOperations<String> messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@Scheduled(fixedDelay=1000)
	public void sendQuotes() {
		for (Quote quote : this.quoteGenerator.generateQuotes()) {
			if (logger.isTraceEnabled()) {
				logger.trace("Sending quote " + quote);
			}
			this.messagingTemplate.convertAndSend("/topic/price.stock." + quote.getTicker(), quote);
		}
	}


	private static class StockQuoteGenerator {

		private static final MathContext mathContext = new MathContext(2);

		private final Random random = new Random();

		private final Map<String, String> prices = new ConcurrentHashMap<>();


		public StockQuoteGenerator() {
			this.prices.put("CTXS", "24.30");
			this.prices.put("DELL", "13.03");
			this.prices.put("EMC", "24.13");
			this.prices.put("GOOG", "893.49");
			this.prices.put("MSFT", "34.21");
			this.prices.put("ORCL", "31.22");
			this.prices.put("RHT", "48.30");
			this.prices.put("VMW", "66.98");
		}

		public Set<Quote> generateQuotes() {
			Set<Quote> quotes = new HashSet<>();
			for (String ticker : this.prices.keySet()) {
				BigDecimal price = getPrice(ticker);
				quotes.add(new Quote(ticker, price));
			}
			return quotes;
		}

		private BigDecimal getPrice(String ticker) {
			BigDecimal seedPrice = new BigDecimal(this.prices.get(ticker), mathContext);
			double range = seedPrice.multiply(new BigDecimal(0.02)).doubleValue();
			BigDecimal priceChange = new BigDecimal(String.valueOf(this.random.nextDouble() * range), mathContext);
			return seedPrice.add(priceChange);
		}

	}
}
