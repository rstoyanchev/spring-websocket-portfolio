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


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.samples.portfolio.web.support.client.StompMessageHandler;
import org.springframework.samples.portfolio.web.support.client.StompSession;
import org.springframework.samples.portfolio.web.support.client.WebSocketStompClient;
import org.springframework.util.Assert;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.jetty.JettyWebSocketClient;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.fail;


public class StompWebSocketLoadTestClient {

	private static Log logger = LogFactory.getLog(StompWebSocketLoadTestClient.class);

	private static final int NUMBER_OF_USERS = 500;

	private static final int NUMBER_OF_MESSAGES_TO_BROADCAST = 2000;

	private static final int THREAD_POOL_SIZE = 25;



	public static void main(String[] args) throws Exception {

		// Modify host and port below to match wherever StompWebSocketServer.java is running!!
		// When StompWebSocketServer starts it prints the selected available

		String host = "localhost";
		if (args.length > 0) {
			host = args[0];
		}

		int port = 8080;
		if (args.length > 1) {
			port = Integer.valueOf(args[1]);
		}

		String url = "http://" + host + ":" + port + "/home";
		logger.debug("Sending warm-up HTTP request to " + url);
		HttpStatus status = new RestTemplate().getForEntity(url, Void.class).getStatusCode();
		Assert.state(status == HttpStatus.OK);

		final CountDownLatch connectLatch = new CountDownLatch(NUMBER_OF_USERS);
		final CountDownLatch subscribeLatch = new CountDownLatch(NUMBER_OF_USERS);
		final CountDownLatch messageLatch = new CountDownLatch(NUMBER_OF_USERS);
		final CountDownLatch disconnectLatch = new CountDownLatch(NUMBER_OF_USERS);

		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

		Executor executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
		org.eclipse.jetty.websocket.client.WebSocketClient jettyClient = new WebSocketClient(executor);
		JettyWebSocketClient webSocketClient = new JettyWebSocketClient(jettyClient);
		webSocketClient.start();

		try {
			URI uri = new URI("ws://" + host + ":" + port + "/stomp/websocket");
			WebSocketStompClient stompClient = new WebSocketStompClient(uri, null, webSocketClient);
			stompClient.setMessageConverter(new StringMessageConverter());

			logger.debug("Connecting and subscribing " + NUMBER_OF_USERS + " users ");
			StopWatch stopWatch = new StopWatch("STOMP Broker Relay WebSocket Load Tests");
			stopWatch.start();

			for (int i=0; i < NUMBER_OF_USERS; i++) {
				stompClient.connect(new ConsumerStompMessageHandler(
						NUMBER_OF_MESSAGES_TO_BROADCAST, connectLatch, subscribeLatch, messageLatch, disconnectLatch, failure));
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

			stopWatch.stop();
			logger.debug("Finished: " + stopWatch.getLastTaskTimeMillis() + " millis");

			logger.debug("Broadcasting " + NUMBER_OF_MESSAGES_TO_BROADCAST + " messages to " + NUMBER_OF_USERS + " users ");
			stopWatch.start();

			stompClient.connect(new ProducerStompMessageHandler(NUMBER_OF_MESSAGES_TO_BROADCAST, failure));

			if (failure.get() != null) {
				throw new AssertionError("Test failed", failure.get());
			}
			if (!messageLatch.await(5 * 60 * 1000, TimeUnit.MILLISECONDS)) {
				fail("Not all handlers received every message, remaining: " + messageLatch.getCount());
			}
//			Thread.sleep(10000);
			if (!disconnectLatch.await(5000, TimeUnit.MILLISECONDS)) {
				fail("Not all disconnects completed, remaining: " + disconnectLatch.getCount());
			}

			stopWatch.stop();
			logger.debug("Finished: " + stopWatch.getLastTaskTimeMillis() + " millis");
		}
		catch (Throwable t) {
			t.printStackTrace();
		}
		finally {
			webSocketClient.stop();
		}

		logger.debug("Exiting");
		System.exit(0);
	}


	private static class ConsumerStompMessageHandler implements StompMessageHandler {

		private final int expectedMessageCount;

		private final CountDownLatch connectLatch;

		private final CountDownLatch subscribeLatch;

		private final CountDownLatch messageLatch;

		private final CountDownLatch disconnectLatch;

		private final AtomicReference<Throwable> failure;

		private StompSession stompSession;

		private AtomicInteger messageCount = new AtomicInteger(0);


		public ConsumerStompMessageHandler(int expectedMessageCount, CountDownLatch connectLatch,
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
		public void afterConnected(StompSession stompSession, StompHeaderAccessor headers) {
			this.connectLatch.countDown();
			this.stompSession = stompSession;
			stompSession.subscribe("/topic/greeting", "receipt1");
		}

		@Override
		public void handleReceipt(String receiptId) {
			this.subscribeLatch.countDown();
		}

		@Override
		public void handleMessage(Message<byte[]> message) {
			if (this.messageCount.incrementAndGet() == this.expectedMessageCount) {
				this.messageLatch.countDown();
				this.stompSession.disconnect();
			}
		}

		@Override
		public void handleError(Message<byte[]> message) {
			this.failure.set(new Exception(new String(message.getPayload(), Charset.forName("UTF-8"))));
		}

		@Override
		public void afterDisconnected() {
			logger.trace("Disconnected in " + this.stompSession);
			this.disconnectLatch.countDown();
		}
	}

	private static class ProducerStompMessageHandler implements StompMessageHandler {

		private final int numberOfMessagesToBroadcast;
		private final AtomicReference<Throwable> failure;

		public ProducerStompMessageHandler(int numberOfMessagesToBroadcast, AtomicReference<Throwable> failure) {
			this.numberOfMessagesToBroadcast = numberOfMessagesToBroadcast;
			this.failure = failure;
		}

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
	}
}
