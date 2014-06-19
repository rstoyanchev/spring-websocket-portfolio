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

package org.springframework.samples.portfolio.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class JmxConfig {

	@Autowired
	private WebSocketHandler subProtocolWebSocketHandler;

	@Autowired
	private ThreadPoolTaskExecutor clientInboundChannelExecutor;

	@Autowired
	private ThreadPoolTaskExecutor clientOutboundChannelExecutor;

	@Autowired
	private ThreadPoolTaskScheduler messageBrokerSockJsTaskScheduler;

	@Bean
	public MBeanExporter exporter() {
		Map<String, Object> beans = new HashMap<String, Object>();
		beans.put("portfolioSample:name=webSocketStats", monitor());
		MBeanExporter exporter = new MBeanExporter();
		exporter.setBeans(beans);
		return exporter;
	}

	@Bean
	public WebSocketStats monitor() {
		return new WebSocketStats((SubProtocolWebSocketHandler) this.subProtocolWebSocketHandler,
				this.clientInboundChannelExecutor, this.clientOutboundChannelExecutor,
				this.messageBrokerSockJsTaskScheduler);
	}

	@Scheduled(fixedDelay=10000)
	public void logMonitorStats() {
		monitor().logStats();
	}

}
