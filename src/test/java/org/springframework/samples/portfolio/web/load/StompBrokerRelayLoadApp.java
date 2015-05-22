/*
 * Copyright 2002-2015 the original author or authors.
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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.messaging.simp.config.AbstractMessageBrokerConfiguration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StopWatch;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.socket.messaging.DefaultSimpUserRegistry;

/**
 * A load app that measures the throughput of messages sent through the
 * {@link org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler}
 * as well as the resulting messages broadcast back from the message broker.
 *
 * <p>This is not a full end-to-end app and does not involve WebSocket clients.
 * The test manually creates messages representing STOMP frames and sends them
 * to the "clientInboundChannel" (simulating clients) and to the "brokerChannel"
 * (for broadcasting messages). Messages received from the message broker are
 * captured through a {@link StompBrokerRelayLoadApp.TestMessageHandler}
 * subscribed to the "clientOutboundChannel".
 *
 * <p>The test can be configured with the number of users to simulate as well
 * as the number of messages to broadcast. Note that increasing the number of
 * users above certain levels may require configuration changes in the broker
 * (e.g. increase file descriptor limits on RabbitMQ). Check the message broker
 * log files for error messages.
 *
 * @author Rossen Stoyanchev
 */
public class StompBrokerRelayLoadApp {

	public static final int NUMBER_OF_USERS = 250;

	public static final int NUMBER_OF_MESSAGES_TO_BROADCAST = 100;

	public static final String DEFAULT_DESTINATION = "/topic/brokerTests-global";


	private AbstractSubscribableChannel clientInboundChannel;

	private TestMessageHandler clientOutboundMessageHandler;

	private SimpMessagingTemplate brokerMessagingTemplate;

	private StopWatch stopWatch;


	public static void main(String[] args) throws InterruptedException {

		StompBrokerRelayLoadApp app = new StompBrokerRelayLoadApp();
		try {
			app.runTest();
		}
		catch (Throwable t) {
			t.printStackTrace();
		}

		System.exit(0);
	}


	private void runTest() throws InterruptedException {

		AnnotationConfigWebApplicationContext cxt = new AnnotationConfigWebApplicationContext();
		cxt.register(MessageConfig.class);
		cxt.refresh();

		this.clientInboundChannel = cxt.getBean("clientInboundChannel", AbstractSubscribableChannel.class);
		this.clientOutboundMessageHandler = cxt.getBean(TestMessageHandler.class);
		this.brokerMessagingTemplate = cxt.getBean(SimpMessagingTemplate.class);

		this.stopWatch = new StopWatch("STOMP Broker Relay Load Tests");

		CountDownLatch brokerAvailabilityLatch = cxt.getBean(CountDownLatch.class);
		brokerAvailabilityLatch.await(5000, TimeUnit.MILLISECONDS);

		List<String> sessionIds = generateIds("session", NUMBER_OF_USERS);
		List<String> subscriptionIds = generateIds("subscription", NUMBER_OF_USERS);
		List<String> receiptIds = generateIds("receipt", NUMBER_OF_USERS);

		connect(sessionIds);
		subscribe(sessionIds, subscriptionIds, receiptIds);

		Person person = new Person();
		person.setName("Joe");

		broadcast(DEFAULT_DESTINATION, person, NUMBER_OF_MESSAGES_TO_BROADCAST, NUMBER_OF_USERS);

		disconnect(sessionIds);
	}

	private List<String> generateIds(String idPrefix, int count) {
		List<String> ids = new ArrayList<>(count);
		for (int i=0; i < count; i++) {
			ids.add(idPrefix + i);
		}
		return Collections.unmodifiableList(ids);
	}

	private void connect(List<String> sessionIds) throws InterruptedException {

		System.out.print("Connecting " + sessionIds.size() + " users ");
		this.stopWatch.start();

		for (String sessionId : sessionIds) {
			StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
			headerAccessor.setHeartbeat(0, 0);
			headerAccessor.setSessionId(sessionId);
			Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headerAccessor.getMessageHeaders());
			this.clientInboundChannel.send(message);
		}

		List<String> expectedIds = new ArrayList<>(sessionIds);
		while (!expectedIds.isEmpty()) {
			Message<?> message = this.clientOutboundMessageHandler.awaitMessage(5000);
			assertNotNull("No more messages, expected " + expectedIds.size() + " more ids: " + expectedIds, message);
			StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
			assertEquals(StompCommand.CONNECTED, headerAccessor.getCommand());
			assertTrue(expectedIds.remove(headerAccessor.getSessionId()));
			if (expectedIds.size() % 100 == 0) {
				System.out.print(".");
			}
		}

		this.stopWatch.stop();
		System.out.println(" (" + this.stopWatch.getLastTaskTimeMillis() + " millis)");
	}

	private void subscribe(List<String> sessionIds, List<String> subscriptionIds, List<String> receiptIds)
			throws InterruptedException {

		System.out.print("Subscribing all users");
		this.stopWatch.start();

		for (int i=0; i < sessionIds.size(); i++) {
			StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
			headerAccessor.setSessionId(sessionIds.get(i));
			headerAccessor.setSubscriptionId(subscriptionIds.get(i));
			headerAccessor.setDestination(DEFAULT_DESTINATION);
			headerAccessor.setReceipt(receiptIds.get(i));
			Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headerAccessor.getMessageHeaders());
			this.clientInboundChannel.send(message);
		}

		List<String> expectedIds = new ArrayList<>(receiptIds);
		while (!expectedIds.isEmpty()) {
			Message<?> message = this.clientOutboundMessageHandler.awaitMessage(5000);
			assertNotNull("No more messages, expected " + expectedIds.size() + " more ids: " + expectedIds, message);
			StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
			assertEquals(StompCommand.RECEIPT, headers.getCommand());
			assertTrue(expectedIds.remove(headers.getReceiptId()));
			if (expectedIds.size() % 100 == 0) {
				System.out.print(".");
			}
		}

		this.stopWatch.stop();
		System.out.println("(" + this.stopWatch.getLastTaskTimeMillis() + " millis)");
	}

	private void broadcast(String destination, Person person, int sendCount, int numberOfSubscribers)
			throws InterruptedException {

		System.out.print("Broadcasting " + sendCount + " messages to " + numberOfSubscribers + " users ");
		this.stopWatch.start();

		for (int i=0; i < sendCount; i++) {
			this.brokerMessagingTemplate.convertAndSend(destination, person);
		}

		int remaining = sendCount * numberOfSubscribers;
		while (remaining > 0) {
			Message<?> message = this.clientOutboundMessageHandler.awaitMessage(5000);
			assertNotNull("No more messages, expected " + remaining + " more id(s)", message);
			StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
			assertEquals(StompCommand.MESSAGE, headers.getCommand());
			assertEquals(destination, headers.getDestination());
			assertEquals("{\"name\":\"Joe\"}", new String((byte[]) message.getPayload()));
			remaining--;
			if (remaining % 10000 == 0) {
				System.out.print(".");
			}
		}

		this.stopWatch.stop();
		System.out.println("(" + this.stopWatch.getLastTaskTimeMillis() + " millis)");
	}

	private void disconnect(List<String> sessionIds) {

		System.out.print("Disconnecting... ");
		this.stopWatch.start("Disconnect");

		for (String sessionId : sessionIds) {
			StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
			headerAccessor.setSessionId(sessionId);
			Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headerAccessor.getMessageHeaders());
			this.clientInboundChannel.send(message);
		}
		this.stopWatch.stop();
		System.out.println("(" + this.stopWatch.getLastTaskTimeMillis() + " millis)");
	}


	@Configuration
	static class MessageConfig extends AbstractMessageBrokerConfiguration
			implements ApplicationListener<ApplicationEvent> {

		private final CountDownLatch brokerAvailabilityLatch = new CountDownLatch(1);


		@Override
		protected SimpUserRegistry createLocalUserRegistry() {
			return new DefaultSimpUserRegistry();
		}

		@Override
		protected void configureMessageBroker(MessageBrokerRegistry registry) {
			registry.enableStompBrokerRelay("/topic/");
		}

		@Override
		protected void configureClientInboundChannel(ChannelRegistration registration) {
			registration.taskExecutor().corePoolSize(4);
		}

		@Override
		protected void configureClientOutboundChannel(ChannelRegistration registration) {
			registration.taskExecutor().corePoolSize(4);
		}

		@Bean
		public TestMessageHandler clientOutboundMessageHandler() {
			return new TestMessageHandler();
		}

		@Bean
		@SuppressWarnings("unused")
		public CountDownLatch getBrokerAvailabilityLatch() {
			return this.brokerAvailabilityLatch;
		}

		@Override
		public void onApplicationEvent(ApplicationEvent event) {

			if (event instanceof ContextRefreshedEvent) {

				// We're only interested in broker relay message handling
				simpAnnotationMethodMessageHandler().stop();
				userDestinationMessageHandler().stop();

				// Register to capture broadcast messages
				clientOutboundChannel().subscribe(clientOutboundMessageHandler());
			}
			else if (event instanceof BrokerAvailabilityEvent) {

				// Broker open for business
				this.brokerAvailabilityLatch.countDown();
			}
		}

	}

	@SuppressWarnings("unused")
	static class Person {

		private String name;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}


	static class TestMessageHandler implements MessageHandler {

		private final BlockingQueue<Message<?>> messages = new LinkedBlockingQueue<>();


		public Message<?> awaitMessage(long timeoutInMillis) throws InterruptedException {
			return this.messages.poll(timeoutInMillis, TimeUnit.MILLISECONDS);
		}

		@Override
		public void handleMessage(Message<?> message) throws MessagingException {
			this.messages.add(message);
		}
	}

}