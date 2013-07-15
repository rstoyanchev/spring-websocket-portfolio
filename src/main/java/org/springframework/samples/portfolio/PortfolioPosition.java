package org.springframework.samples.portfolio;

public class PortfolioPosition {

	private final String company;

	private final String ticker;

	private final double price;

	private final int shares;


	public PortfolioPosition(String company, String ticker, double price, int shares) {
		this.company = company;
		this.ticker = ticker;
		this.price = price;
		this.shares = shares;
	}

	public PortfolioPosition(PortfolioPosition other, int shares) {
		this.company = other.company;
		this.ticker = other.ticker;
		this.price = other.price;
		this.shares = shares;
	}

	public String getCompany() {
		return this.company;
	}

	public String getTicker() {
		return this.ticker;
	}

	public double getPrice() {
		return this.price;
	}

	public int getShares() {
		return this.shares;
	}

}
