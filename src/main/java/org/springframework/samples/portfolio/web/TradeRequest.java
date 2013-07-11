package org.springframework.samples.portfolio.web;


public class TradeRequest {

	private String ticker;

	private int shares;

	private TradeRequestAction action;


	public String getTicker() {
		return this.ticker;
	}

	public void setTicker(String ticker) {
		this.ticker = ticker;
	}

	public int getShares() {
		return this.shares;
	}

	public void setShares(int shares) {
		this.shares = shares;
	}

	public TradeRequestAction getAction() {
		return this.action;
	}

	public void setAction(TradeRequestAction action) {
		this.action = action;
	}

	@Override
	public String toString() {
		return "[ticker=" + this.ticker + ", shares=" + this.shares + ", action=" + this.action + "]";
	}


	public enum TradeRequestAction {
		Buy, Sell;
	}

}
