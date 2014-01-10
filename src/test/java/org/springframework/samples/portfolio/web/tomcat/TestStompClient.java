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

package org.springframework.samples.portfolio.web.tomcat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompDecoder;
import org.springframework.messaging.simp.stomp.StompEncoder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ExceptionWebSocketHandlerDecorator;
import org.springframework.web.socket.handler.LoggingWebSocketHandlerDecorator;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A simple STOMP over WebSocket client
 * <p>
 * Only covers connect, subscribe & receive, as well as the sending of messages.
 * It is not robust and not very well protected against errors. However, it
 * provides just enough for the purpose of testing the stock portfolio sample.
 * <p>
 * The client can be extended and adapted for more advanced testing scenarios.
 *
 * @author Rossen Stoyanchev
 */
public class TestStompClient {

	private static Log logger = LogFactory.getLog(TestStompClient.class);

	public static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");


	private final URI handshakeUri;

	private final WebSocketHttpHeaders handshakeHeaders;

	private WebSocketSession session;

	private final Map<String, MessageHandler> subscriptionHandlers = new ConcurrentHashMap<String, MessageHandler>();

	private final AtomicInteger subscriptionIndex = new AtomicInteger(0);

	private final StompEncoder encoder = new StompEncoder();

	private final StompDecoder decoder = new StompDecoder();

	private MessageConverter messageConverter = new MappingJackson2MessageConverter();


	public TestStompClient(URI handshakeUri, WebSocketHttpHeaders handshakeHeaders) {
		this.handshakeUri = handshakeUri;
		this.handshakeHeaders = handshakeHeaders;
	}


	public void setMessageConverter(MessageConverter messageConverter) {
		this.messageConverter = messageConverter;
	}

	public MessageConverter getMessageConverter() {
		return this.messageConverter;
	}

	public void connect(MessageHandler connectedHandler) {

		WebSocketHandler wsHandler = new StompWebSocketHandler(connectedHandler);
		wsHandler = new ExceptionWebSocketHandlerDecorator(wsHandler);
		wsHandler = new LoggingWebSocketHandlerDecorator(wsHandler);

		StandardWebSocketClient client = new StandardWebSocketClient();
		try {
			this.session = client.doHandshake(wsHandler, this.handshakeHeaders, this.handshakeUri).get();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public void subscribe(String destination, MessageHandler messageHandler) {

		String id = String.valueOf(this.subscriptionIndex.getAndIncrement());
		this.subscriptionHandlers.put(id, messageHandler);

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		headers.setSubscriptionId(id);
		headers.setDestination(destination);

		Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).setHeaders(headers).build();
		byte[] bytes = encoder.encode(message);
		try {
			this.session.sendMessage(new TextMessage(new String(bytes, DEFAULT_CHARSET)));
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void send(String destination, Object payload) {

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
		headers.setDestination(destination);

		Message<byte[]> message =
				(Message<byte[]>) this.messageConverter.toMessage(payload, new MessageHeaders(headers.toMap()));

		byte[] bytes = this.encoder.encode(message);

		try {
			this.session.sendMessage(new TextMessage(new String(bytes, DEFAULT_CHARSET)));
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}

	}

	public void disconnect() {
		if (this.session != null) {
			try {
				this.session.close();
			}
			catch (IOException e) {
				logger.error("Failed to disconnect", e);
			}
		}
	}

	private class StompWebSocketHandler extends AbstractWebSocketHandler {

		private final MessageHandler connectedHandler;


		private StompWebSocketHandler(MessageHandler connectedHandler) {
			this.connectedHandler = connectedHandler;
		}

		@Override
		public void afterConnectionEstablished(WebSocketSession session) throws IOException {

			StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
			headers.setAcceptVersion("1.1,1.2");
			headers.setHeartbeat(0, 0);
			Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).setHeaders(headers).build();

			TextMessage textMessage = new TextMessage(new String(encoder.encode(message), DEFAULT_CHARSET));
			session.sendMessage(textMessage);
		}

		@Override
		protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {

			ByteBuffer payload = ByteBuffer.wrap(textMessage.getPayload().getBytes(DEFAULT_CHARSET));
			Message<byte[]> message = decoder.decode(payload);
			if (message == null) {
				logger.error("Invalid message: " + message);
			}

			StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
			if (StompCommand.CONNECTED.equals(headers.getCommand())) {
				this.connectedHandler.handleMessage(message);
			}
			else if (StompCommand.MESSAGE.equals(headers.getCommand())) {
				String subscriptionId = headers.getSubscriptionId();
				MessageHandler subscriptionHandler = TestStompClient.this.subscriptionHandlers.get(subscriptionId);
				if (subscriptionHandler == null) {
					logger.error("No subscribed handler for message: " + message);
				}
				subscriptionHandler.handleMessage(message);
			}
		}
	}
}
