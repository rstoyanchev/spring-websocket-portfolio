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

package org.springframework.samples.portfolio.web.support.client;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompDecoder;
import org.springframework.messaging.simp.stomp.StompEncoder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;

public class WebSocketStompClient implements StompClient {

	private static Log logger = LogFactory.getLog(WebSocketStompClient.class);


	private final URI uri;

	private final WebSocketHttpHeaders headers;

	private final WebSocketClient webSocketClient;

	private MessageConverter messageConverter;


	public WebSocketStompClient(URI uri, WebSocketHttpHeaders headers, WebSocketClient webSocketClient) {
		this.uri = uri;
		this.headers = headers;
		this.webSocketClient = webSocketClient;
	}

	public void setMessageConverter(MessageConverter messageConverter) {
		this.messageConverter = messageConverter;
	}

	@Override
	public void connect(StompMessageHandler stompMessageHandler) {
		try {
			StompWebSocketHandler webSocketHandler = new StompWebSocketHandler(stompMessageHandler, this.messageConverter);
			this.webSocketClient.doHandshake(webSocketHandler, this.headers, this.uri).get();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}


	private static class StompWebSocketHandler extends AbstractWebSocketHandler {

		private static final Charset UTF_8 = Charset.forName("UTF-8");

		private final StompMessageHandler stompMessageHandler;

		private final MessageConverter messageConverter;

		private final StompEncoder encoder = new StompEncoder();

		private final StompDecoder decoder = new StompDecoder();


		private StompWebSocketHandler(StompMessageHandler delegate) {
			this(delegate, new MappingJackson2MessageConverter());
		}

		private StompWebSocketHandler(StompMessageHandler delegate, MessageConverter messageConverter) {
			this.stompMessageHandler = delegate;
			this.messageConverter = messageConverter;
		}


		@Override
		public void afterConnectionEstablished(WebSocketSession session) throws Exception {

			StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
			headers.setAcceptVersion("1.1,1.2");
			headers.setHeartbeat(0, 0);
			Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).setHeaders(headers).build();

			TextMessage textMessage = new TextMessage(new String(this.encoder.encode(message), UTF_8));
			session.sendMessage(textMessage);
		}

		@Override
		protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {

			ByteBuffer payload = ByteBuffer.wrap(textMessage.getPayload().getBytes(UTF_8));
			List<Message<byte[]>> messages = this.decoder.decode(payload);

			for (Message message : messages) {
				StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
				if (StompCommand.CONNECTED.equals(headers.getCommand())) {
					WebSocketStompSession stompSession = new WebSocketStompSession(session, this.messageConverter);
					this.stompMessageHandler.afterConnected(stompSession, headers);
				}
				else if (StompCommand.MESSAGE.equals(headers.getCommand())) {
					this.stompMessageHandler.handleMessage(message);
				}
				else if (StompCommand.RECEIPT.equals(headers.getCommand())) {
					this.stompMessageHandler.handleReceipt(headers.getReceiptId());
				}
				else if (StompCommand.ERROR.equals(headers.getCommand())) {
					this.stompMessageHandler.handleError(message);
				}
				else if (StompCommand.ERROR.equals(headers.getCommand())) {
					this.stompMessageHandler.afterDisconnected();
				}
				else {
					logger.debug("Unhandled message " + message);
				}
			}
		}

		@Override
		public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
			logger.error("WebSocket transport error", exception);
		}

		@Override
		public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
			this.stompMessageHandler.afterDisconnected();
		}
	}


}
