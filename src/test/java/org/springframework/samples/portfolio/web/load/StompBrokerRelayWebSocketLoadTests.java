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

package org.springframework.samples.portfolio.web.load;


import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.samples.portfolio.web.StompMessageHandler;
import org.springframework.samples.portfolio.web.StompSession;
import org.springframework.samples.portfolio.web.TomcatWebSocketTestServer;
import org.springframework.samples.portfolio.web.WebSocketStompClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.SocketUtils;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import javax.servlet.ServletRegistration;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class StompBrokerRelayWebSocketLoadTests {

	private static final StringMessageConverter MESSAGE_CONVERTER = new StringMessageConverter();


	private static int port;

	private static TomcatWebSocketTestServer server;

	private ThreadPoolTaskExecutor connectExecutor;

	private StopWatch stopWatch;


	@BeforeClass
	public static void setUpOnce() throws Exception {

		port = SocketUtils.findAvailableTcpPort();
		server = new TomcatWebSocketTestServer(port);
		server.deployConfig(DispatcherServletInitializer.class);
		server.start();

		String url = "http://localhost:" + port + "/home";
		System.out.println("Sending warm-up HTTP request to " + url);
		assertEquals(HttpStatus.OK, new RestTemplate().getForEntity(url, Void.class).getStatusCode());
	}

	@AfterClass
	public static void tearDownOnce() throws Exception {
		if (server != null) {
			try {
				server.undeployConfig();
			}
			catch (Throwable t) {
				System.err.println("Failed to undeploy application");
				t.printStackTrace();
			}

			try {
				server.stop();
			}
			catch (Throwable t) {
				System.err.println("Failed to stop server");
				t.printStackTrace();
			}
		}
	}

	@Before
	public void setup() {
		this.stopWatch = new StopWatch("STOMP Broker Relay WebSocket Load Tests");
		this.connectExecutor = new ThreadPoolTaskExecutor();
		this.connectExecutor.setCorePoolSize(25);
		this.connectExecutor.afterPropertiesSet();
	}

	@After
	public void teardown() {
		this.connectExecutor.shutdown();
	}


	@Test
	public void simpleBroadcast() throws Exception {

		final int numberOfUsers = 750;
		final int numberOfMessagesToBroadcast = 100;

		final CountDownLatch connectLatch = new CountDownLatch(numberOfUsers);
		final CountDownLatch subscribeLatch = new CountDownLatch(numberOfUsers);
		final CountDownLatch messageLatch = new CountDownLatch(numberOfUsers);
		final CountDownLatch disconnectLatch = new CountDownLatch(numberOfUsers);

		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

		StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
		webSocketClient.setTaskExecutor(this.connectExecutor);

		URI uri = new URI("ws://localhost:" + port + "/stomp/websocket");
		WebSocketStompClient stompClient = new WebSocketStompClient(uri, null, webSocketClient);
		stompClient.setMessageConverter(new StringMessageConverter());

		System.out.print("Connecting and subscribing " + numberOfUsers + " users ");
		this.stopWatch.start();

		for (int i=0; i < numberOfUsers; i++) {

			stompClient.connect(new StompMessageHandler() {

				private StompSession stompSession;

				private AtomicInteger messageCount = new AtomicInteger(0);


				@Override
				public void afterConnected(StompSession stompSession, StompHeaderAccessor headers) {
					connectLatch.countDown();
					this.stompSession = stompSession;
					stompSession.subscribe("/topic/greeting", "receipt1");
				}

				@Override
				public void handleReceipt(String receiptId) {
					subscribeLatch.countDown();
				}

				@Override
				public void handleMessage(Message<byte[]> message) {
					if (this.messageCount.incrementAndGet() == numberOfMessagesToBroadcast) {
						messageLatch.countDown();
						stompSession.disconnect();
					}
				}

				@Override
				public void handleError(Message<byte[]> message) {
					failure.set(new Exception(new String(message.getPayload(), Charset.forName("UTF-8"))));
				}

				@Override
				public void afterDisconnected() {
					disconnectLatch.countDown();
				}
			});

		}

		if (failure.get() != null) {
			throw new AssertionError("Test failed", failure.get());
		}

		if (!connectLatch.await(5000, TimeUnit.MILLISECONDS)) {
			fail("Not all users connected, remaining: " + connectLatch.getCount());
		}

		if (!subscribeLatch.await(5000, TimeUnit.MILLISECONDS)) {
			fail("Not all users subscribed, remaining: " + connectLatch.getCount());
		}

		this.stopWatch.stop();
		System.out.println(" (" + this.stopWatch.getLastTaskTimeMillis() + " millis)");

		System.out.print("Broadcasting " + numberOfMessagesToBroadcast + " messages to " + numberOfUsers + " users ");
		this.stopWatch.start();

		stompClient.connect(new StompMessageHandler() {

			@Override
			public void afterConnected(StompSession session, StompHeaderAccessor headers) {
				try {
					for (int i=0; i < numberOfMessagesToBroadcast; i++) {
						session.send("/app/greeting", "hello");
					}
				}
				catch (Throwable t) {
					failure.set(t);
				}
			}

			@Override
			public void handleMessage(Message<byte[]> message) {}

			@Override
			public void handleReceipt(String receiptId) {}

			@Override
			public void handleError(Message<byte[]> message) {
				failure.set(new Exception(new String(message.getPayload(), Charset.forName("UTF-8"))));
			}

			@Override
			public void afterDisconnected() {}
		});

		if (failure.get() != null) {
			throw new AssertionError("Test failed", failure.get());
		}

		if (!messageLatch.await(20000, TimeUnit.MILLISECONDS)) {
			fail("Not all handlers received every message, remaining: " + messageLatch.getCount());
		}

		if (!disconnectLatch.await(5000, TimeUnit.MILLISECONDS)) {
			fail("Not all disconnects completed, remaining: " + disconnectLatch.getCount());
		}

		this.stopWatch.stop();
		System.out.println("(" + this.stopWatch.getLastTaskTimeMillis() + " millis)");
	}


	public static class DispatcherServletInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

		@Override
		protected Class<?>[] getRootConfigClasses() {
			return null;
		}

		@Override
		protected Class<?>[] getServletConfigClasses() {
			return new Class<?>[] { WebSocketConfig.class };
		}

		@Override
		protected String[] getServletMappings() {
			return new String[] { "/" };
		}

		@Override
		protected void customizeRegistration(ServletRegistration.Dynamic registration) {
			registration.setInitParameter("dispatchOptionsRequest", "true");
			registration.setAsyncSupported(true);
		}

	}

	@Configuration
	@EnableWebMvc
	@EnableWebSocketMessageBroker
	static class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer
			implements ApplicationListener<ContextClosedEvent> {

		private StompMessageCounter messageCounter = new StompMessageCounter();


		@Override
		public void registerStompEndpoints(StompEndpointRegistry registry) {
			registry.addEndpoint("/stomp").withSockJS();
		}

		@Override
		public void configureMessageBroker(MessageBrokerRegistry registry) {
			registry.enableStompBrokerRelay("/topic/");
			registry.setApplicationDestinationPrefixes("/app");
		}

		@Override
		public void configureClientInboundChannel(ChannelRegistration registration) {
			registration.setInterceptors(new ChannelInterceptorAdapter() {
				@Override
				public Message<?> preSend(Message<?> message, MessageChannel channel) {
					messageCounter.handleMessage(message);
					return super.preSend(message, channel);
				}
			}).taskExecutor().corePoolSize(4);
		}

		@Override
		public void configureClientOutboundChannel(ChannelRegistration registration) {
			registration.setInterceptors(new ChannelInterceptorAdapter() {
				@Override
				public Message<?> preSend(Message<?> message, MessageChannel channel) {
					messageCounter.handleMessage(message);
					return super.preSend(message, channel);
				}
			}).taskExecutor().corePoolSize(4);
		}

		@Override
		public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
			messageConverters.add(new StringMessageConverter());
			return false;
		}

		@Bean
		public HomeController homeController() {
			return new HomeController();
		}

		@Override
		public void onApplicationEvent(ContextClosedEvent event) {
		 	System.out.println("Server side counters: " + this.messageCounter);
		}
	}

	@RestController
	static class HomeController {

		public static final DateFormat DATE_FORMAT = SimpleDateFormat.getDateInstance();


		@RequestMapping(value="/home", method = RequestMethod.GET)
		public void home() {
		}

		@MessageMapping("/greeting")
		public String handleGreeting(String greeting) {
			return "[" + DATE_FORMAT.format(new Date()) + "] " + greeting;
		}
	}

	private static class StompMessageCounter {

		private static Map<StompCommand, AtomicInteger> counters = new HashMap<StompCommand, AtomicInteger>();

		public StompMessageCounter() {
			for (StompCommand command : StompCommand.values()) {
				this.counters.put(command, new AtomicInteger(0));
			}
		}

		public void handleMessage(Message<?> message) {
			StompHeaderAccessor headers =StompHeaderAccessor.wrap(message);
			AtomicInteger counter = this.counters.get(headers.getCommand());
			counter.incrementAndGet();
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (StompCommand command : StompCommand.values()) {
				AtomicInteger counter = this.counters.get(command);
				if (counter.get() > 0) {
					sb.append("(").append(command.name()).append(": ").append(counter.get()).append(") ");
				}
			}
			return sb.toString();
		}

	}

}
