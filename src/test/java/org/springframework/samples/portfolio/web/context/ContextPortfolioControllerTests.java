/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.samples.portfolio.web.context;

import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.samples.portfolio.service.Trade;
import org.springframework.samples.portfolio.web.support.TestPrincipal;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;


/**
 * Tests for PortfolioController that rely on the Spring TestContext framework to
 * load the actual Spring configuration. The test strategy here is to test the
 * behavior of controllers using the actual Spring configuration while using
 * the TestContext framework ensures that Spring configuration is loaded only
 * once per test class.
 *
 * <p>The test manually creates messages representing STOMP frames and sends them
 * to the "clientInboundChannel" simulating clients by setting the session id and
 * user headers of the message accordingly.
 *
 * <p>Test ChannelInterceptor implementations are installed on the "brokerChannel"
 * and the "clientOutboundChannel" in order to capture messages sent through
 * them. Although not the case here, often a controller method will
 * not send any messages at all. In such cases it might be necessary to inject
 * the controller with "mock" services in order to verify message handling.
 *
 * <p>Note the (optional) use of TestConfig, which removes MessageHandler
 * subscriptions to message channels except the handler that delegates to
 * annotated controller methods. This allows focusing on controllers.
 *
 * @author Rossen Stoyanchev
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {
		ContextPortfolioControllerTests.TestWebSocketConfig.class,
		ContextPortfolioControllerTests.TestConfig.class
})
public class ContextPortfolioControllerTests {

	@Autowired private AbstractSubscribableChannel clientInboundChannel;

	@Autowired private AbstractSubscribableChannel clientOutboundChannel;

	@Autowired private AbstractSubscribableChannel brokerChannel;

	private TestChannelInterceptor clientOutboundChannelInterceptor;

	private TestChannelInterceptor brokerChannelInterceptor;


	@Before
	public void setUp() throws Exception {

		this.brokerChannelInterceptor = new TestChannelInterceptor();
		this.clientOutboundChannelInterceptor = new TestChannelInterceptor();

		this.brokerChannel.addInterceptor(this.brokerChannelInterceptor);
		this.clientOutboundChannel.addInterceptor(this.clientOutboundChannelInterceptor);
	}


	@Test
	public void getPositions() throws Exception {

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		headers.setSubscriptionId("0");
		headers.setDestination("/app/positions");
		headers.setSessionId("0");
		headers.setUser(new TestPrincipal("fabrice"));
		headers.setSessionAttributes(new HashMap<String, Object>());
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

		this.clientOutboundChannelInterceptor.setIncludedDestinations("/app/positions");
		this.clientInboundChannel.send(message);

		Message<?> reply = this.clientOutboundChannelInterceptor.awaitMessage(5);
		assertNotNull(reply);

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
		Message<byte[]> message = MessageBuilder.createMessage(payload, headers.getMessageHeaders());

		this.brokerChannelInterceptor.setIncludedDestinations("/user/**");
		this.clientInboundChannel.send(message);

		Message<?> positionUpdate = this.brokerChannelInterceptor.awaitMessage(5);
		assertNotNull(positionUpdate);

		StompHeaderAccessor positionUpdateHeaders = StompHeaderAccessor.wrap(positionUpdate);
		assertEquals("/user/fabrice/queue/position-updates", positionUpdateHeaders.getDestination());

		String json = new String((byte[]) positionUpdate.getPayload(), Charset.forName("UTF-8"));
		new JsonPathExpectationsHelper("$.ticker").assertValue(json, "DELL");
		new JsonPathExpectationsHelper("$.shares").assertValue(json, 75);
	}

	@Configuration
	@EnableScheduling
	@ComponentScan(
			basePackages="org.springframework.samples",
			excludeFilters = @ComponentScan.Filter(type= FilterType.ANNOTATION, value = Configuration.class)
	)
	@EnableWebSocketMessageBroker
	static class TestWebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

		@Autowired
		Environment env;

		@Override
		public void registerStompEndpoints(StompEndpointRegistry registry) {
			registry.addEndpoint("/portfolio").withSockJS();
		}

		@Override
		public void configureMessageBroker(MessageBrokerRegistry registry) {
//			registry.enableSimpleBroker("/queue/", "/topic/");
			registry.enableStompBrokerRelay("/queue/", "/topic/");
			registry.setApplicationDestinationPrefixes("/app");
		}
	}

	/**
	 * Configuration class that un-registers MessageHandler's it finds in the
	 * ApplicationContext from the message channels they are subscribed to...
	 * except the message handler used to invoke annotated message handling methods.
	 * The intent is to reduce additional processing and additional messages not
	 * related to the test.
	 */
	@Configuration
	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	static class TestConfig implements ApplicationListener<ContextRefreshedEvent> {

		@Autowired
		private List<SubscribableChannel> channels;

		@Autowired
		private List<MessageHandler> handlers;


		@Override
		public void onApplicationEvent(ContextRefreshedEvent event) {
			for (MessageHandler handler : handlers) {
				if (handler instanceof SimpAnnotationMethodMessageHandler) {
					continue;
				}
				for (SubscribableChannel channel :channels) {
					channel.unsubscribe(handler);
				}
			}
		}
	}
}