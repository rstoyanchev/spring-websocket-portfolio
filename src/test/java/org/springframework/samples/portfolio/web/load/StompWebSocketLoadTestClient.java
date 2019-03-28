/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.samples.portfolio.web.load;


import static org.junit.Assert.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.ConnectionLostException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.JettyXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;


public class StompWebSocketLoadTestClient {

	private static Log logger = LogFactory.getLog(StompWebSocketLoadTestClient.class);

	private static final int NUMBER_OF_USERS = 200;

	private static final int BROADCAST_MESSAGE_COUNT = 2000;


	public static void main(String[] args) throws Exception {

		// Modify host and port below to match wherever StompWebSocketServer.java is running!!
		// When StompWebSocketServer starts it prints the selected available

		String host = "localhost";
		if (args.length > 0) {
			host = args[0];
		}

		int port = 37232;
		if (args.length > 1) {
			port = Integer.valueOf(args[1]);
		}

		String homeUrl = "http://{host}:{port}/home";
		logger.debug("Sending warm-up HTTP request to " + homeUrl);
		HttpStatus status = new RestTemplate().getForEntity(homeUrl, Void.class, host, port).getStatusCode();
		Assert.state(status == HttpStatus.OK);

		final CountDownLatch connectLatch = new CountDownLatch(NUMBER_OF_USERS);
		final CountDownLatch subscribeLatch = new CountDownLatch(NUMBER_OF_USERS);
		final CountDownLatch messageLatch = new CountDownLatch(NUMBER_OF_USERS);
		final CountDownLatch disconnectLatch = new CountDownLatch(NUMBER_OF_USERS);

		final AtomicReference<Throwable> failure = new AtomicReference<>();

		StandardWebSocketClient webSocketClient = new StandardWebSocketClient();

		HttpClient jettyHttpClient = new HttpClient();
		jettyHttpClient.setMaxConnectionsPerDestination(1000);
		jettyHttpClient.setExecutor(new QueuedThreadPool(1000));
		jettyHttpClient.start();

		List<Transport> transports = new ArrayList<>();
		transports.add(new WebSocketTransport(webSocketClient));
		transports.add(new JettyXhrTransport(jettyHttpClient));

		SockJsClient sockJsClient = new SockJsClient(transports);

		try {
			ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
			taskScheduler.afterPropertiesSet();

			String stompUrl = "ws://{host}:{port}/stomp";
			WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
			stompClient.setMessageConverter(new StringMessageConverter());
			stompClient.setTaskScheduler(taskScheduler);
			stompClient.setDefaultHeartbeat(new long[] {0, 0});

			logger.debug("Connecting and subscribing " + NUMBER_OF_USERS + " users ");
			StopWatch stopWatch = new StopWatch("STOMP Broker Relay WebSocket Load Tests");
			stopWatch.start();

			List<ConsumerStompSessionHandler> consumers = new ArrayList<>();
			for (int i=0; i < NUMBER_OF_USERS; i++) {
				consumers.add(new ConsumerStompSessionHandler(BROADCAST_MESSAGE_COUNT, connectLatch,
						subscribeLatch, messageLatch, disconnectLatch, failure));
				stompClient.connect(stompUrl, consumers.get(i), host, port);
			}

			if (failure.get() != null) {
				throw new AssertionError("Test failed", failure.get());
			}
			if (!connectLatch.await(5000, TimeUnit.MILLISECONDS)) {
				fail("Not all users connected, remaining: " + connectLatch.getCount());
			}
			if (!subscribeLatch.await(5000, TimeUnit.MILLISECONDS)) {
				fail("Not all users subscribed, remaining: " + subscribeLatch.getCount());
			}

			stopWatch.stop();
			logger.debug("Finished: " + stopWatch.getLastTaskTimeMillis() + " millis");

			logger.debug("Broadcasting " + BROADCAST_MESSAGE_COUNT + " messages to " + NUMBER_OF_USERS + " users ");
			stopWatch.start();

			ProducerStompSessionHandler producer = new ProducerStompSessionHandler(BROADCAST_MESSAGE_COUNT, failure);
			stompClient.connect(stompUrl, producer, host, port);
			stompClient.setTaskScheduler(taskScheduler);

			if (failure.get() != null) {
				throw new AssertionError("Test failed", failure.get());
			}
			if (!messageLatch.await(60 * 1000, TimeUnit.MILLISECONDS)) {
				for (ConsumerStompSessionHandler consumer : consumers) {
					if (consumer.messageCount.get() < consumer.expectedMessageCount) {
						logger.debug(consumer);
					}
				}
			}
			if (!messageLatch.await(60 * 1000, TimeUnit.MILLISECONDS)) {
				fail("Not all handlers received every message, remaining: " + messageLatch.getCount());
			}

			producer.session.disconnect();
			if (!disconnectLatch.await(5000, TimeUnit.MILLISECONDS)) {
				fail("Not all disconnects completed, remaining: " + disconnectLatch.getCount());
			}

			stopWatch.stop();
			logger.debug("Finished: " + stopWatch.getLastTaskTimeMillis() + " millis");

			System.out.println("\nPress any key to exit...");
			System.in.read();
		}
		catch (Throwable t) {
			t.printStackTrace();
		}
		finally {
			jettyHttpClient.stop();
		}

		logger.debug("Exiting");
		System.exit(0);
	}


	private static class ConsumerStompSessionHandler extends StompSessionHandlerAdapter {

		private final int expectedMessageCount;

		private final CountDownLatch connectLatch;

		private final CountDownLatch subscribeLatch;

		private final CountDownLatch messageLatch;

		private final CountDownLatch disconnectLatch;

		private final AtomicReference<Throwable> failure;

		private AtomicInteger messageCount = new AtomicInteger(0);


		public ConsumerStompSessionHandler(int expectedMessageCount, CountDownLatch connectLatch,
				CountDownLatch subscribeLatch, CountDownLatch messageLatch, CountDownLatch disconnectLatch,
				AtomicReference<Throwable> failure) {

			this.expectedMessageCount = expectedMessageCount;
			this.connectLatch = connectLatch;
			this.subscribeLatch = subscribeLatch;
			this.messageLatch = messageLatch;
			this.disconnectLatch = disconnectLatch;
			this.failure = failure;
		}

		@Override
		public void afterConnected(final StompSession session, StompHeaders connectedHeaders) {
			this.connectLatch.countDown();
			session.setAutoReceipt(true);
			session.subscribe("/topic/greeting", new StompFrameHandler() {
				@Override
				public Type getPayloadType(StompHeaders headers) {
					return String.class;
				}

				@Override
				public void handleFrame(StompHeaders headers, Object payload) {
					if (messageCount.incrementAndGet() == expectedMessageCount) {
						messageLatch.countDown();
						disconnectLatch.countDown();
						session.disconnect();
					}
				}
			}).addReceiptTask(new Runnable() {
				@Override
				public void run() {
					subscribeLatch.countDown();
				}
			});
		}

		@Override
		public void handleTransportError(StompSession session, Throwable exception) {
			logger.error("Transport error", exception);
			this.failure.set(exception);
			if (exception instanceof ConnectionLostException) {
				this.disconnectLatch.countDown();
			}
		}

		@Override
		public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable ex) {
			logger.error("Handling exception", ex);
			this.failure.set(ex);
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			Exception ex = new Exception(headers.toString());
			logger.error("STOMP ERROR frame", ex);
			this.failure.set(ex);
		}

		@Override
		public String toString() {
			return "ConsumerStompSessionHandler[messageCount=" + this.messageCount + "]";
		}
	}

	private static class ProducerStompSessionHandler extends StompSessionHandlerAdapter {

		private final int numberOfMessagesToBroadcast;

		private final AtomicReference<Throwable> failure;

		private StompSession session;


		public ProducerStompSessionHandler(int numberOfMessagesToBroadcast, AtomicReference<Throwable> failure) {
			this.numberOfMessagesToBroadcast = numberOfMessagesToBroadcast;
			this.failure = failure;
		}

		@Override
		public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
			this.session = session;
			int i =0;
			try {
				for ( ; i < this.numberOfMessagesToBroadcast; i++) {
					session.send("/app/greeting", "hello");
				}
			}
			catch (Throwable t) {
				logger.error("Message sending failed at " + i, t);
				failure.set(t);
			}
		}

		@Override
		public void handleTransportError(StompSession session, Throwable exception) {
			logger.error("Transport error", exception);
			this.failure.set(exception);
		}

		@Override
		public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable ex) {
			logger.error("Handling exception", ex);
			this.failure.set(ex);
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			Exception ex = new Exception(headers.toString());
			logger.error("STOMP ERROR frame", ex);
			this.failure.set(ex);
		}
	}

}
