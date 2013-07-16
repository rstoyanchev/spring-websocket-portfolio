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

	public PortfolioPosition(PortfolioPosition other, int sharesToAddOrSubtract) {
		this.company = other.company;
		this.ticker = other.ticker;
		this.price = other.price;
		this.shares = other.shares + sharesToAddOrSubtract;
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

	@Override
	public String toString() {
		return "PortfolioPosition [company=" + this.company + ", ticker=" + this.ticker
				+ ", price=" + this.price + ", shares=" + this.shares + "]";
	}

}
