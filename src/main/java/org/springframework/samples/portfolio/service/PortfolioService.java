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

import java.util.HashMap;
import java.util.Map;

import org.springframework.samples.portfolio.Portfolio;
import org.springframework.samples.portfolio.PortfolioPosition;
import org.springframework.stereotype.Service;


/**
 * @author Rob Winch
 */
@Service
public class PortfolioService {

	// user -> Portfolio
	private final Map<String, Portfolio> portfolioLookup = new HashMap<>();


	public PortfolioService() {

		Portfolio portfolio = new Portfolio();
		portfolio.addPosition(new PortfolioPosition("Citrix Systems, Inc.", "CTXS", 24.30, 75));
		portfolio.addPosition(new PortfolioPosition("Dell Inc.", "DELL", 13.44, 50));
		portfolio.addPosition(new PortfolioPosition("Microsoft", "MSFT", 34.15, 33));
		portfolio.addPosition(new PortfolioPosition("Oracle", "ORCL", 31.22, 45));
		this.portfolioLookup.put("fabrice", portfolio);

		portfolio = new Portfolio();
		portfolio.addPosition(new PortfolioPosition("EMC Corporation", "EMC", 24.30, 75));
		portfolio.addPosition(new PortfolioPosition("Google Inc", "GOOG", 905.09, 5));
		portfolio.addPosition(new PortfolioPosition("VMware, Inc.", "VMW", 65.58, 23));
		portfolio.addPosition(new PortfolioPosition("Red Hat", "RHT", 48.30, 15));
		this.portfolioLookup.put("paulson", portfolio);
	}


	public Portfolio findPortfolio(String username) {
		Portfolio portfolio = this.portfolioLookup.get(username);
		if (portfolio == null) {
			throw new IllegalArgumentException(username);
		}
		return portfolio;
	}

}
