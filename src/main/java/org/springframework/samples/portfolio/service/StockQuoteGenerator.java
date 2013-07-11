package org.springframework.samples.portfolio.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;


public class StockQuoteGenerator {

	private static final MathContext mathContext = new MathContext(2);

	private final Random random = new Random();

	private final Map<String, String> quotes = new ConcurrentHashMap<String, String>();

	private final List<String> tickers;

	private Iterator<String> tickersIterator;


	public StockQuoteGenerator() {

		this.quotes.put("CTXS", "24.30");
		this.quotes.put("DELL", "13.03");
		this.quotes.put("EMC", "24.13");
		this.quotes.put("GOOG", "893.49");
		this.quotes.put("MSFT", "34.21");
		this.quotes.put("ORCL", "31.22");
		this.quotes.put("RHT", "48.30");
		this.quotes.put("VMW", "66.98");

		this.tickers = new ArrayList<String>(this.quotes.keySet());
		this.tickersIterator = this.tickers.iterator();
	}

	public Quote nextQuote() {
		if (!this.tickersIterator.hasNext()) {
			Collections.shuffle(this.tickers);
			this.tickersIterator = this.tickers.iterator();
		}
		String ticker = this.tickersIterator.next();
		BigDecimal price = getPrice(ticker);
		return new Quote(ticker, price);
	}

	private BigDecimal getPrice(String ticker) {
		BigDecimal seedPrice = new BigDecimal(this.quotes.get(ticker), mathContext);
		double range = seedPrice.multiply(new BigDecimal(0.02)).doubleValue();
		BigDecimal priceChange = new BigDecimal(String.valueOf(this.random.nextDouble() * range), mathContext);
		return seedPrice.add(priceChange);
	}


	public static void main(String[] args) {
		StockQuoteGenerator gen = new StockQuoteGenerator();
		for (int i=0 ; i < gen.quotes.size(); i++) {
			System.out.println(gen.nextQuote());
		}
		System.out.println("");
		for (int i=0 ; i < gen.quotes.size(); i++) {
			System.out.println(gen.nextQuote());
		}
	}

}
