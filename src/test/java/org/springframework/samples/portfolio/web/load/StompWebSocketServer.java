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


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.samples.portfolio.web.TomcatWebSocketTestServer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.SocketUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurationSupport;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import javax.servlet.ServletRegistration;
import java.io.File;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


public class StompWebSocketServer {

	private static final StringMessageConverter MESSAGE_CONVERTER;

	static {
		DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
		resolver.setDefaultMimeType(MimeTypeUtils.TEXT_PLAIN);

		MESSAGE_CONVERTER = new StringMessageConverter();
		MESSAGE_CONVERTER.setContentTypeResolver(resolver);
	}


	public static void main(String[] args) {

		TomcatWebSocketTestServer server = null;

		try {
			int port = SocketUtils.findAvailableTcpPort();

			// Write the port to tmp file for StompWebSocketClient
			File file = new File(System.getProperty("java.io.tmpdir"), "StompWebSocketTest.tmp");
			file.deleteOnExit();
			PrintWriter writer = new PrintWriter(file);
			writer.write(port);
			writer.close();

			server = new TomcatWebSocketTestServer(port);
			server.deployConfig(DispatcherServletInitializer.class);
			server.start();

			System.out.println("Running on port " + port);
			System.out.println("Press any key to stop");
			System.in.read();

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
		catch (Throwable t) {
			t.printStackTrace();
		}

		System.exit(0);
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
	@EnableScheduling
	static class WebSocketConfig extends WebSocketMessageBrokerConfigurationSupport {


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
		public void configureClientOutboundChannel(ChannelRegistration registration) {
			registration.taskExecutor().corePoolSize(100);
		}

		@Override
		public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
			messageConverters.add(MESSAGE_CONVERTER);
			return false;
		}

		@Bean
		public WebSocketMessageBrokerStatsMonitor statsMonitor() {
			return new WebSocketMessageBrokerStatsMonitor(
					(SubProtocolWebSocketHandler) subProtocolWebSocketHandler(), clientOutboundChannelExecutor());
		}

		@Bean
		public HomeController homeController() {
			return new HomeController();
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
