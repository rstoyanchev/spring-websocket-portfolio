/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.portfolio.web.standalone;

import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import org.springframework.context.support.StaticApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.samples.portfolio.service.PortfolioService;
import org.springframework.samples.portfolio.service.PortfolioServiceImpl;
import org.springframework.samples.portfolio.service.Trade;
import org.springframework.samples.portfolio.web.PortfolioController;
import org.springframework.samples.portfolio.web.support.TestPrincipal;
import org.springframework.test.util.JsonPathExpectationsHelper;

/**
 * Tests for PortfolioController that instantiate directly the minimum
 * infrastructure necessary to test annotated controller methods and do not load
 * Spring configuration.
 *
 * Tests can create a Spring {@link org.springframework.messaging.Message} that
 * represents a STOMP frame and send it directly to the
 * SimpAnnotationMethodMessageHandler responsible for invoking annotated controller
 * methods.
 *
 * Test message channels can be used to detect any messages the controller may send.
 * It's also easy to inject the controller with a test-specific TradeService to
 * verify what trades are getting executed.
 *
 * The test strategy here is to test the behavior of controllers taking into
 * account controller annotations and nothing more. The tests are simpler to write
 * and faster to executed. They provide the most amount of control and that is good
 * for writing as many controller tests as needed. Separate tests are still required
 * to verify the Spring configuration but those tests should be fewer overall.
 *
 * @author Rossen Stoyanchev
 */
public class StandalonePortfolioControllerTests {

	private PortfolioService portfolioService;

	private TestTradeService tradeService;

	private TestMessageChannel clientOutboundChannel;

	private TestAnnotationMethodHandler annotationMethodHandler;


	@Before
	public void setup() {

		this.portfolioService = new PortfolioServiceImpl();
		this.tradeService = new TestTradeService();
		PortfolioController controller = new PortfolioController(this.portfolioService, this.tradeService);

		this.clientOutboundChannel = new TestMessageChannel();

		this.annotationMethodHandler = new TestAnnotationMethodHandler(
				new TestMessageChannel(), clientOutboundChannel, new SimpMessagingTemplate(new TestMessageChannel()));

		this.annotationMethodHandler.registerHandler(controller);
		this.annotationMethodHandler.setDestinationPrefixes(Arrays.asList("/app"));
		this.annotationMethodHandler.setMessageConverter(new MappingJackson2MessageConverter());
		this.annotationMethodHandler.setApplicationContext(new StaticApplicationContext());
		this.annotationMethodHandler.afterPropertiesSet();
	}


	@Test
	public void getPositions() throws Exception {

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		headers.setSubscriptionId("0");
		headers.setDestination("/app/positions");
		headers.setSessionId("0");
		headers.setUser(new TestPrincipal("fabrice"));
		headers.setSessionAttributes(new HashMap<String, Object>());
		Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).setHeaders(headers).build();

		this.annotationMethodHandler.handleMessage(message);

		assertEquals(1, this.clientOutboundChannel.getMessages().size());
		Message<?> reply = this.clientOutboundChannel.getMessages().get(0);

		StompHeaderAccessor replyHeaders = StompHeaderAccessor.wrap(reply);
		assertEquals("0", replyHeaders.getSessionId());
		assertEquals("0", replyHeaders.getSubscriptionId());
		assertEquals("/app/positions", replyHeaders.getDestination());

		String json = new String((byte[]) reply.getPayload(), Charset.forName("UTF-8"));
		new JsonPathExpectationsHelper("$[0].company").assertValue(json, "Citrix Systems, Inc.");
		new JsonPathExpectationsHelper("$[1].company").assertValue(json, "Dell Inc.");
		new JsonPathExpectationsHelper("$[2].company").assertValue(json, "Microsoft");
		new JsonPathExpectationsHelper("$[3].company").assertValue(json, "Oracle");
	}

	@Test
	public void executeTrade() throws Exception {

		Trade trade = new Trade();
		trade.setAction(Trade.TradeAction.Buy);
		trade.setTicker("DELL");
		trade.setShares(25);

		byte[] payload = new ObjectMapper().writeValueAsBytes(trade);

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
		headers.setDestination("/app/trade");
		headers.setSessionId("0");
		headers.setUser(new TestPrincipal("fabrice"));
		headers.setSessionAttributes(new HashMap<String, Object>());
		Message<byte[]> message = MessageBuilder.withPayload(payload).setHeaders(headers).build();

		this.annotationMethodHandler.handleMessage(message);

		assertEquals(1, this.tradeService.getTrades().size());
		Trade actual = this.tradeService.getTrades().get(0);

		assertEquals(Trade.TradeAction.Buy, actual.getAction());
		assertEquals("DELL", actual.getTicker());
		assertEquals(25, actual.getShares());
		assertEquals("fabrice", actual.getUsername());
	}


	/**
	 * An extension of SimpAnnotationMethodMessageHandler that exposes a (public)
	 * method for manually registering a controller, rather than having it
	 * auto-discovered in the Spring ApplicationContext.
	 */
	private static class TestAnnotationMethodHandler extends SimpAnnotationMethodMessageHandler {

		public TestAnnotationMethodHandler(SubscribableChannel inChannel, MessageChannel outChannel,
				SimpMessageSendingOperations brokerTemplate) {

			super(inChannel, outChannel, brokerTemplate);
		}

		public void registerHandler(Object handler) {
			super.detectHandlerMethods(handler);
		}
	}

}
