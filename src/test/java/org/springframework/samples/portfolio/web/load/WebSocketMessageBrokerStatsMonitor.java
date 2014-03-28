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
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;


public class WebSocketMessageBrokerStatsMonitor {

	private static Log logger = LogFactory.getLog(WebSocketMessageBrokerStatsMonitor.class);

	private final WebSocketHandlerStats webSocketHandlerStats;

	private final ThreadPoolExecutorStats outboundExecutorStats;



	public WebSocketMessageBrokerStatsMonitor(SubProtocolWebSocketHandler webSocketHandler,
			ThreadPoolTaskExecutor outboundTaskExecutor) {

		this.webSocketHandlerStats = new WebSocketHandlerStats(webSocketHandler);
		this.outboundExecutorStats = new ThreadPoolExecutorStats(outboundTaskExecutor);
	}


	@Scheduled(fixedDelay=10000)
	public void printStats() {

		if (logger.isDebugEnabled()) {

			logger.debug("WebSocket[" + this.webSocketHandlerStats.toString() +
					"], outboundChannel" + this.outboundExecutorStats.toString());

		}
	}

	private static String formatByteCount(long bytes) {
		int unit = 1024;
		if (bytes < unit) return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), "KMGTPE".charAt(exp - 1));
	}


	private static class WebSocketHandlerStats {

		private final Map<String, WebSocketSession> webSocketSessions;


		private WebSocketHandlerStats(SubProtocolWebSocketHandler webSocketHandler) {
			this.webSocketSessions = (Map<String, WebSocketSession>)
					new DirectFieldAccessor(webSocketHandler).getPropertyValue("sessions");
		}

		private int calculateSendBufferSize() {
			int sendBufferSize = 0;
			for (WebSocketSession session : this.webSocketSessions.values()) {
				ConcurrentWebSocketSessionDecorator concurrentSession = (ConcurrentWebSocketSessionDecorator) session;
				sendBufferSize += concurrentSession.getBufferSize();
			}
			return sendBufferSize;
		}

		public String toString() {
			return webSocketSessions.size() + " sessions, " + formatByteCount(calculateSendBufferSize()) + " send buffer" ;
		}
	}

	private static class ThreadPoolExecutorStats {

		private final ThreadPoolExecutor executor;


		private ThreadPoolExecutorStats(ThreadPoolTaskExecutor taskExecutor) {
			this.executor = taskExecutor.getThreadPoolExecutor();
		}

		public String toString() {
			String desc = this.executor.toString();
			desc = desc.substring(desc.indexOf("pool"));
			return "[" + desc;
		}
	}

}
