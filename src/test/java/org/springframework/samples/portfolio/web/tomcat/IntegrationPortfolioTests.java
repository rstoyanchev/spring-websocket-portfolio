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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.samples.portfolio.config.DispatcherServletInitializer;
import org.springframework.samples.portfolio.config.WebSecurityInitializer;
import org.springframework.samples.portfolio.service.Trade;
import org.springframework.samples.portfolio.web.StompWebSocketClient;
import org.springframework.samples.portfolio.web.TomcatWebSocketTestServer;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.SocketUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
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

	private final static WebSocketHttpHeaders headers = new WebSocketHttpHeaders();


	@BeforeClass
	public static void setup() throws Exception {

		port = SocketUtils.findAvailableTcpPort();

		server = new TomcatWebSocketTestServer(port);
		server.deployConfig(DispatcherServletInitializer.class, WebSecurityInitializer.class);
		server.start();

		loginAndSaveJsessionIdCookie("fabrice", "fab123", headers);
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

		final StompWebSocketClient stompClient = new StompWebSocketClient(
				new URI("ws://localhost:" + port + "/portfolio/websocket"), this.headers);

		stompClient.connect(new MessageHandler() {
			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				stompClient.subscribe("/app/positions", new MessageHandler() {
					@Override
					public void handleMessage(Message<?> reply) throws MessagingException {
						logger.debug("Got " + new String((byte[]) reply.getPayload()));
						try {
							String json = new String((byte[]) reply.getPayload(), Charset.forName("UTF-8"));
							new JsonPathExpectationsHelper("$[0].company").assertValue(json, "Citrix Systems, Inc.");
							new JsonPathExpectationsHelper("$[1].company").assertValue(json, "Dell Inc.");
							new JsonPathExpectationsHelper("$[2].company").assertValue(json, "Microsoft");
							new JsonPathExpectationsHelper("$[3].company").assertValue(json, "Oracle");
						}
						catch (Throwable t) {
							failure.set(t);
						}
						finally {
							latch.countDown();
						}
					}
				});
			}
		});

		if (!latch.await(5, TimeUnit.SECONDS)) {
			fail("Portfolio positions not received");
		}
		else if (failure.get() != null) {
			throw new AssertionError("", failure.get());
		}

		stompClient.disconnect();
	}

	@Test
	public void executeTrade() throws Exception {

		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

		final StompWebSocketClient stompClient = new StompWebSocketClient(
				new URI("ws://localhost:" + port + "/portfolio/websocket"), this.headers);

		stompClient.connect(new MessageHandler() {
			@Override
			public void handleMessage(Message<?> message) throws MessagingException {

				stompClient.subscribe("/user/queue/position-updates", new MessageHandler() {
					@Override
					public void handleMessage(Message<?> reply) throws MessagingException {
						logger.debug("Got " + new String((byte[]) reply.getPayload()));
						try {
							String json = new String((byte[]) reply.getPayload(), Charset.forName("UTF-8"));
							new JsonPathExpectationsHelper("$.shares").assertValue(json, 75);
							new JsonPathExpectationsHelper("$.company").assertValue(json, "Dell Inc.");
						}
						catch (Throwable t) {
							failure.set(t);
						}
						finally {
							latch.countDown();
						}
					}
				});

				try {
					Trade trade = new Trade();
					trade.setAction(Trade.TradeAction.Buy);
					trade.setTicker("DELL");
					trade.setShares(25);

					stompClient.send("/app/trade", trade);
				}
				catch (Throwable t) {
					failure.set(t);
					latch.countDown();
				}
			}
		});

		if (!latch.await(5, TimeUnit.SECONDS)) {
			fail("Trade confirmation not received");
		}
		else if (failure.get() != null) {
			throw new AssertionError("", failure.get());
		}

		stompClient.disconnect();
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

}
