package org.springframework.samples.portfolio.service;

import java.math.BigDecimal;


public class Quote {

	private final String ticker;

	private final BigDecimal price;


	public Quote(String ticker, BigDecimal price) {
		this.ticker = ticker;
		this.price = price;
	}


	public String getTicker() {
		return this.ticker;
	}

	public BigDecimal getPrice() {
		return this.price;
	}

	@Override
	public String toString() {
		return "Quote [ticker=" + this.ticker + ", this.price=" + price + "]";
	}

}
