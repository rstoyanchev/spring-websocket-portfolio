/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.portfolio.web.tomcat;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.samples.portfolio.PortfolioPosition;
import org.springframework.samples.portfolio.config.DispatcherServletInitializer;
import org.springframework.samples.portfolio.config.WebConfig;
import org.springframework.samples.portfolio.config.WebSecurityInitializer;
import org.springframework.samples.portfolio.service.Trade;
import org.springframework.samples.portfolio.web.support.TomcatWebSocketTestServer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.SocketUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import static org.junit.Assert.*;

/**
 * End-to-end integration tests that run an embedded Tomcat server and establish
 * an actual WebSocket session using
 * {@link org.springframework.web.socket.client.standard.StandardWebSocketClient}.
 * as well as a simple STOMP/WebSocket client created to support these tests.
 *
 * The test strategy here is to test from the perspective of a client connecting
 * to a server and therefore it is a much more complete test. However, writing
 * and maintaining these tests is a bit more involved.
 *
 * An all-encapsulating strategy might be to write the majority of tests using
 * server-side testing (either standalone or with Spring configuration) with
 * end-to-end integration tests serving as a higher-level verification but
 * overall fewer in number.
 *
 * @author Rossen Stoyanchev
 */
public class IntegrationPortfolioTests {

	private static Log logger = LogFactory.getLog(IntegrationPortfolioTests.class);

	private static int port;

	private static TomcatWebSocketTestServer server;

	private static SockJsClient sockJsClient;

	private final static WebSocketHttpHeaders headers = new WebSocketHttpHeaders();


	@BeforeClass
	public static void setup() throws Exception {

		// Since test classpath includes both embedded Tomcat and Jetty we need to
		// set a Spring profile explicitly to bypass WebSocket engine detection.
		// See {@link org.springframework.samples.portfolio.config.WebSocketConfig}

		// This test is not supported with Jetty because it doesn't seem to support
		// deployment withspecific ServletContainerInitializer's at for testing

		System.setProperty("spring.profiles.active", "test.tomcat");

		port = SocketUtils.findAvailableTcpPort();

		server = new TomcatWebSocketTestServer(port);
		server.deployWithInitializer(TestDispatcherServletInitializer.class, WebSecurityInitializer.class);
		server.start();

		loginAndSaveJsessionIdCookie("fabrice", "fab123", headers);

		List<Transport> transports = new ArrayList<>();
		transports.add(new WebSocketTransport(new StandardWebSocketClient()));
		RestTemplateXhrTransport xhrTransport = new RestTemplateXhrTransport(new RestTemplate());
		transports.add(xhrTransport);

		sockJsClient = new SockJsClient(transports);
	}

	private static void loginAndSaveJsessionIdCookie(final String user, final String password,
			final HttpHeaders headersToUpdate) {

		String url = "http://localhost:" + port + "/login.html";

		new RestTemplate().execute(url, HttpMethod.POST,

				request -> {
					MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
					map.add("username", user);
					map.add("password", password);
					new FormHttpMessageConverter().write(map, MediaType.APPLICATION_FORM_URLENCODED, request);
				},

				response -> {
					headersToUpdate.add("Cookie", response.getHeaders().getFirst("Set-Cookie"));
					return null;
				});
	}

	@AfterClass
	public static void teardown() {
		if (server != null) {
			try {
				server.undeployConfig();
			}
			catch (Throwable t) {
				logger.error("Failed to undeploy application", t);
			}

			try {
				server.stop();
			}
			catch (Throwable t) {
				logger.error("Failed to stop server", t);
			}
		}
	}


	@Test
	public void getPositions() throws Exception {

		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<Throwable> failure = new AtomicReference<>();

		StompSessionHandler handler = new AbstractTestSessionHandler(failure) {

			@Override
			public void afterConnected(final StompSession session, StompHeaders connectedHeaders) {
				session.subscribe("/app/positions", new StompFrameHandler() {
					@Override
					public Type getPayloadType(StompHeaders headers) {
						return byte[].class;
					}

					@Override
					public void handleFrame(StompHeaders headers, Object payload) {
						String json = new String((byte[]) payload);
						logger.debug("Got " + json);
						try {
							new JsonPathExpectationsHelper("$[0].company").assertValue(json, "Citrix Systems, Inc.");
							new JsonPathExpectationsHelper("$[1].company").assertValue(json, "Dell Inc.");
							new JsonPathExpectationsHelper("$[2].company").assertValue(json, "Microsoft");
							new JsonPathExpectationsHelper("$[3].company").assertValue(json, "Oracle");
						}
						catch (Throwable t) {
							failure.set(t);
						}
						finally {
							session.disconnect();
							latch.countDown();
						}
					}
				});
			}
		};

		WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
		stompClient.connect("ws://localhost:{port}/portfolio", this.headers, handler, port);

		if (failure.get() != null) {
			throw new AssertionError("", failure.get());
		}

		if (!latch.await(5, TimeUnit.SECONDS)) {
			fail("Portfolio positions not received");
		}
	}

	@Test
	public void executeTrade() throws Exception {

		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<Throwable> failure = new AtomicReference<>();

		StompSessionHandler handler = new AbstractTestSessionHandler(failure) {

			@Override
			public void afterConnected(final StompSession session, StompHeaders connectedHeaders) {
				session.subscribe("/user/queue/position-updates", new StompFrameHandler() {
					@Override
					public Type getPayloadType(StompHeaders headers) {
						return PortfolioPosition.class;
					}

					@Override
					public void handleFrame(StompHeaders headers, Object payload) {
						PortfolioPosition position = (PortfolioPosition) payload;
						logger.debug("Got " + position);
						try {
							assertEquals(75, position.getShares());
							assertEquals("Dell Inc.", position.getCompany());
						}
						catch (Throwable t) {
							failure.set(t);
						}
						finally {
							session.disconnect();
							latch.countDown();
						}
					}
				});

				try {
					Trade trade = new Trade();
					trade.setAction(Trade.TradeAction.Buy);
					trade.setTicker("DELL");
					trade.setShares(25);
					session.send("/app/trade", trade);
				}
				catch (Throwable t) {
					failure.set(t);
					latch.countDown();
				}
			}
		};

		WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
		stompClient.setMessageConverter(new MappingJackson2MessageConverter());
		stompClient.connect("ws://localhost:{port}/portfolio", headers, handler, port);

		if (!latch.await(10, TimeUnit.SECONDS)) {
			fail("Trade confirmation not received");
		}
		else if (failure.get() != null) {
			throw new AssertionError("", failure.get());
		}
	}


	public static class TestDispatcherServletInitializer extends DispatcherServletInitializer {

		@Override
		protected Class<?>[] getServletConfigClasses() {
			return new Class[] { WebConfig.class, TestWebSocketConfig.class };
		}
	}

	@Configuration
	@EnableScheduling
	@ComponentScan(
			basePackages="org.springframework.samples",
			excludeFilters = @ComponentScan.Filter(type= FilterType.ANNOTATION, value = Configuration.class)
	)
	@EnableWebSocketMessageBroker
	static class TestWebSocketConfig implements WebSocketMessageBrokerConfigurer {

		@Autowired
		Environment env;

		@Override
		public void registerStompEndpoints(StompEndpointRegistry registry) {
			// The test classpath includes both Tomcat and Jetty, so let's be explicit
			DefaultHandshakeHandler handler = new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy());
			registry.addEndpoint("/portfolio").setHandshakeHandler(handler).withSockJS();
		}

		@Override
		public void configureMessageBroker(MessageBrokerRegistry registry) {
			registry.enableSimpleBroker("/queue/", "/topic/");
//			registry.enableStompBrokerRelay("/queue/", "/topic/");
			registry.setApplicationDestinationPrefixes("/app");
		}
	}

	private static abstract class AbstractTestSessionHandler extends StompSessionHandlerAdapter {

		private final AtomicReference<Throwable> failure;


		public AbstractTestSessionHandler(AtomicReference<Throwable> failure) {
			this.failure = failure;
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			logger.error("STOMP ERROR frame: " + headers.toString());
			this.failure.set(new Exception(headers.toString()));
		}

		@Override
		public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable ex) {
			logger.error("Handler exception", ex);
			this.failure.set(ex);
		}

		@Override
		public void handleTransportError(StompSession session, Throwable ex) {
			logger.error("Transport failure", ex);
			this.failure.set(ex);
		}
	}

}
