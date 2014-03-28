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


import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.samples.portfolio.web.StompMessageHandler;
import org.springframework.samples.portfolio.web.StompSession;
import org.springframework.samples.portfolio.web.WebSocketStompClient;
import org.springframework.util.Assert;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.fail;


public class StompWebSocketClient {


	public static void main(String[] args) throws Exception {

		int port;

		if (args.length == 1) {
			port = Integer.valueOf(args[0]);
		}
		else {
			port = readPort();
		}

		String url = "http://localhost:" + port + "/home";
		System.out.println("Sending warm-up HTTP request to " + url);
		HttpStatus status = new RestTemplate().getForEntity(url, Void.class).getStatusCode();
		Assert.state(status == HttpStatus.OK);

		final int numberOfUsers = 500;
		final int numberOfMessagesToBroadcast = 2000;

		final CountDownLatch connectLatch = new CountDownLatch(numberOfUsers);
		final CountDownLatch subscribeLatch = new CountDownLatch(numberOfUsers);
		final CountDownLatch messageLatch = new CountDownLatch(numberOfUsers);
		final CountDownLatch disconnectLatch = new CountDownLatch(numberOfUsers);

		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

		URI uri = new URI("ws://localhost:" + port + "/stomp/websocket");
		WebSocketStompClient stompClient = new WebSocketStompClient(uri, null, new StandardWebSocketClient());
		stompClient.setMessageConverter(new StringMessageConverter());

		System.out.print("Connecting and subscribing " + numberOfUsers + " users ");
		StopWatch stopWatch = new StopWatch("STOMP Broker Relay WebSocket Load Tests");
		stopWatch.start();

		for (int i=0; i < numberOfUsers; i++) {
			stompClient.connect(new ConsumerStompMessageHandler(
					numberOfMessagesToBroadcast, connectLatch, subscribeLatch, messageLatch, disconnectLatch, failure));
		}

		if (failure.get() != null) {
			throw new AssertionError("Test failed", failure.get());
		}
		else if (!connectLatch.await(5000, TimeUnit.MILLISECONDS)) {
			fail("Not all users connected, remaining: " + connectLatch.getCount());
		}
		else if (!subscribeLatch.await(5000, TimeUnit.MILLISECONDS)) {
			fail("Not all users subscribed, remaining: " + connectLatch.getCount());
		}

		stopWatch.stop();
		System.out.println(" (" + stopWatch.getLastTaskTimeMillis() + " millis)");

		System.out.print("Broadcasting " + numberOfMessagesToBroadcast + " messages to " + numberOfUsers + " users ");
		stopWatch.start();

		stompClient.connect(new ProducerStompMessageHandler(numberOfMessagesToBroadcast, failure));

		if (failure.get() != null) {
			throw new AssertionError("Test failed", failure.get());
		}
		else if (!messageLatch.await(5 * 60 * 1000, TimeUnit.MILLISECONDS)) {
			fail("Not all handlers received every message, remaining: " + messageLatch.getCount());
		}
		else if (!disconnectLatch.await(5000, TimeUnit.MILLISECONDS)) {
			fail("Not all disconnects completed, remaining: " + disconnectLatch.getCount());
		}

		stopWatch.stop();
		System.out.println("(" + stopWatch.getLastTaskTimeMillis() + " millis)");

	}

	private static int readPort() throws IOException {
		File file = new File(System.getProperty("java.io.tmpdir"), "StompWebSocketTest.tmp");
		try {
			FileReader reader = new FileReader(file);
			int value = reader.read();
			reader.close();
			return value;
		}
		catch (Throwable ex) {
			System.err.println("Failed to read port from " + file.toString() + ". " +
					"It looks like StompWebSocketServer.java is not running on this machine. " +
					"Pass port as command line argument instead");
			throw ex;
		}
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
