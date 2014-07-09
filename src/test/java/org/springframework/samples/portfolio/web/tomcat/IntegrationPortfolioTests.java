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

package org.springframework.samples.portfolio.web.tomcat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.samples.portfolio.config.DispatcherServletInitializer;
import org.springframework.samples.portfolio.config.WebConfig;
import org.springframework.samples.portfolio.config.WebSecurityInitializer;
import org.springframework.samples.portfolio.service.Trade;
import org.springframework.samples.portfolio.web.support.client.StompMessageHandler;
import org.springframework.samples.portfolio.web.support.client.StompSession;
import org.springframework.samples.portfolio.web.support.server.TomcatWebSocketTestServer;
import org.springframework.samples.portfolio.web.support.client.WebSocketStompClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.SocketUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.fail;

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
		server.deployConfig(TestDispatcherServletInitializer.class, WebSecurityInitializer.class);
		server.start();

		loginAndSaveJsessionIdCookie("fabrice", "fab123", headers);

		List<Transport> transports = new ArrayList<>();
		transports.add(new WebSocketTransport(new StandardWebSocketClient()));
		RestTemplateXhrTransport xhrTransport = new RestTemplateXhrTransport(new RestTemplate());
		xhrTransport.setRequestHeaders(headers);
		transports.add(xhrTransport);

		sockJsClient = new SockJsClient(transports);
	}

	private static void loginAndSaveJsessionIdCookie(final String user, final String password,
			final HttpHeaders headersToUpdate) {

		String url = "http://localhost:" + port + "/login.html";

		new RestTemplate().execute(url, HttpMethod.POST,

				new RequestCallback() {
					@Override
					public void doWithRequest(ClientHttpRequest request) throws IOException {
						MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
						map.add("username", user);
						map.add("password", password);
						new FormHttpMessageConverter().write(map, MediaType.APPLICATION_FORM_URLENCODED, request);
					}
				},

				new ResponseExtractor<Object>() {
					@Override
					public Object extractData(ClientHttpResponse response) throws IOException {
						headersToUpdate.add("Cookie", response.getHeaders().getFirst("Set-Cookie"));
						return null;
					}
				});
	}

	@AfterClass
	public static void teardown() throws Exception {
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
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

		URI uri = new URI("ws://localhost:" + port + "/portfolio");
		WebSocketStompClient stompClient = new WebSocketStompClient(uri, this.headers, sockJsClient);
		stompClient.setMessageConverter(new MappingJackson2MessageConverter());

		stompClient.connect(new StompMessageHandler() {

			private StompSession stompSession;

			@Override
			public void afterConnected(StompSession stompSession, StompHeaderAccessor headers) {
				stompSession.subscribe("/app/positions", null);
				this.stompSession = stompSession;
			}
			@Override
			public void handleMessage(Message<byte[]> message) {
				StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
				if (!"/app/positions".equals(headers.getDestination())) {
					 failure.set(new IllegalStateException("Unexpected message: " + message));
				}
				logger.debug("Got " + new String((byte[]) message.getPayload()));
				try {
					String json = new String((byte[]) message.getPayload(), Charset.forName("UTF-8"));
					new JsonPathExpectationsHelper("$[0].company").assertValue(json, "Citrix Systems, Inc.");
					new JsonPathExpectationsHelper("$[1].company").assertValue(json, "Dell Inc.");
					new JsonPathExpectationsHelper("$[2].company").assertValue(json, "Microsoft");
					new JsonPathExpectationsHelper("$[3].company").assertValue(json, "Oracle");
				}
				catch (Throwable t) {
					failure.set(t);
				}
				finally {
					this.stompSession.disconnect();
					latch.countDown();
				}
			}

			@Override
			public void handleError(Message<byte[]> message) {
				failure.set(new Exception(new String(message.getPayload(), Charset.forName("UTF-8"))));
			}

			@Override
			public void handleReceipt(String receiptId) {}

			@Override
			public void afterDisconnected() {}
		});

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
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

		URI uri = new URI("ws://localhost:" + port + "/portfolio");
		WebSocketStompClient stompClient = new WebSocketStompClient(uri, this.headers, sockJsClient);
		stompClient.setMessageConverter(new MappingJackson2MessageConverter());

		stompClient.connect(new StompMessageHandler() {

			private StompSession stompSession;

			@Override
			public void afterConnected(StompSession stompSession, StompHeaderAccessor headers) {

				this.stompSession = stompSession;
				this.stompSession.subscribe("/user/queue/position-updates", null);

				try {
					Trade trade = new Trade();
					trade.setAction(Trade.TradeAction.Buy);
					trade.setTicker("DELL");
					trade.setShares(25);

					this.stompSession.send("/app/trade", trade);
				}
				catch (Throwable t) {
					failure.set(t);
					latch.countDown();
				}
			}

			@Override
			public void handleMessage(Message<byte[]> message) {
				StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
				if (!"/user/queue/position-updates".equals(headers.getDestination())) {
					failure.set(new IllegalStateException("Unexpected message: " + message));
				}
				logger.debug("Got " + new String((byte[]) message.getPayload()));
				try {
					String json = new String((byte[]) message.getPayload(), Charset.forName("UTF-8"));
					new JsonPathExpectationsHelper("$.shares").assertValue(json, 75);
					new JsonPathExpectationsHelper("$.company").assertValue(json, "Dell Inc.");
				}
				catch (Throwable t) {
					failure.set(t);
				}
				finally {
					this.stompSession.disconnect();
					latch.countDown();
				}
			}

			@Override
			public void handleError(Message<byte[]> message) {
				StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
				String error = "[Producer] " + accessor.getShortLogMessage(message.getPayload());
				logger.error(error);
				failure.set(new Exception(error));
			}

			@Override
			public void handleReceipt(String receiptId) {}

			@Override
			public void afterDisconnected() {}
		});

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
	static class TestWebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

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
//			registry.enableSimpleBroker("/queue/", "/topic/");
			registry.enableStompBrokerRelay("/queue/", "/topic/");
			registry.setApplicationDestinationPrefixes("/app");
		}
	}

}
