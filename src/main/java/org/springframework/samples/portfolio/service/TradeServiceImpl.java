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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.core.MessageSendingOperations;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.samples.portfolio.Portfolio;
import org.springframework.samples.portfolio.PortfolioPosition;
import org.springframework.samples.portfolio.security.PortfolioAuthentication;
import org.springframework.samples.portfolio.service.Trade.TradeAction;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


@Service
public class TradeServiceImpl implements TradeService, ApplicationListener<BrokerAvailabilityEvent> {

    private static final Log logger = LogFactory.getLog(TradeServiceImpl.class);

    private final MessageSendingOperations<String> messagingTemplate;

    private final PortfolioService portfolioService;

    private final AtomicBoolean brokerAvailable = new AtomicBoolean();

    private final Gson gson = new Gson();


    @Autowired
    public TradeServiceImpl(MessageSendingOperations<String> messagingTemplate, PortfolioService portfolioService) {
        this.messagingTemplate = messagingTemplate;
        this.portfolioService = portfolioService;
    }

    /**
     * In real application a trade is probably executed in an external system, i.e. asynchronously.
     */
    public void executeTrade(Trade trade, Principal principal) {

        Portfolio portfolio = this.portfolioService.findPortfolio(trade.getUsername());
        String ticker = trade.getTicker();
        int sharesToTrade = trade.getShares();

        PortfolioPosition newPosition = (trade.getAction() == TradeAction.Buy) ?
                portfolio.buy(ticker, sharesToTrade) : portfolio.sell(ticker, sharesToTrade);
        final PortfolioAuthentication authentication = (PortfolioAuthentication) principal;
        if (newPosition == null) {
            final JsonObject json = new JsonObject();
            json.addProperty("error", "Rejectred trade.");
            json.add("detail", gson.toJsonTree(trade));
            if (this.brokerAvailable.get())
                this.messagingTemplate.convertAndSend("/topic/session.event." + authentication.getToken(),
                        json.toString());
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON);
        if (this.brokerAvailable.get())
            this.messagingTemplate.convertAndSend("/topic/session.event." + authentication.getToken(),
                    newPosition);
    }

    @Override
    public void onApplicationEvent(BrokerAvailabilityEvent event) {
        this.brokerAvailable.set(event.isBrokerAvailable());
    }

}
