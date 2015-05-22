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
package org.springframework.samples.portfolio;


public class PortfolioPosition {

	private String company;

	private String ticker;

	private double price;

	private int shares;

	private long updateTime;


	public PortfolioPosition(String company, String ticker, double price, int shares) {
		this.company = company;
		this.ticker = ticker;
		this.price = price;
		this.shares = shares;
		this.updateTime = System.currentTimeMillis();
	}

	public PortfolioPosition(PortfolioPosition other, int sharesToAddOrSubtract) {
		this.company = other.company;
		this.ticker = other.ticker;
		this.price = other.price;
		this.shares = other.shares + sharesToAddOrSubtract;
		this.updateTime = System.currentTimeMillis();
	}

	private PortfolioPosition() {
	}

	public String getCompany() {
		return this.company;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public String getTicker() {
		return this.ticker;
	}

	public void setTicker(String ticker) {
		this.ticker = ticker;
	}

	public double getPrice() {
		return this.price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public int getShares() {
		return this.shares;
	}

	public void setShares(int shares) {
		this.shares = shares;
	}

	public long getUpdateTime() {
		return this.updateTime;
	}

	public void setUpdateTime(long updateTime) {
		this.updateTime = updateTime;
	}

	@Override
	public String toString() {
		return "PortfolioPosition [company=" + this.company + ", ticker=" + this.ticker
				+ ", price=" + this.price + ", shares=" + this.shares + "]";
	}

}
