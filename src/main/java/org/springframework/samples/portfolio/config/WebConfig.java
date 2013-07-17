package org.springframework.samples.portfolio.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.handler.AnnotationMethodMessageHandler;
import org.springframework.messaging.simp.handler.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.handler.SimpleUserSessionResolver;
import org.springframework.messaging.simp.handler.UserDestinationMessageHandler;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.messaging.simp.stomp.StompWebSocketHandler;
import org.springframework.messaging.support.channel.ExecutorSubscribableChannel;
import org.springframework.messaging.support.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.support.converter.MessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.sockjs.SockJsService;
import org.springframework.web.socket.sockjs.support.DefaultSockJsService;
import org.springframework.web.socket.sockjs.support.SockJsHttpRequestHandler;


@Configuration
@EnableWebMvc
@EnableScheduling
@ComponentScan(basePackages="org.springframework.samples")
public class WebConfig extends WebMvcConfigurerAdapter {

	private final MessageConverter<?> messageConverter = new MappingJackson2MessageConverter();

	private final SimpleUserSessionResolver userSessionResolver = new SimpleUserSessionResolver();


	@Bean
	public SimpleUrlHandlerMapping handlerMapping() {

		SockJsService sockJsService = new DefaultSockJsService(taskScheduler());
		HttpRequestHandler requestHandler = new SockJsHttpRequestHandler(sockJsService, stompWebSocketHandler());

		SimpleUrlHandlerMapping hm = new SimpleUrlHandlerMapping();
		hm.setOrder(-1);
		hm.setUrlMap(Collections.singletonMap("/portfolio/**", requestHandler));
		return hm;
	}

	@Bean
	public StompWebSocketHandler stompWebSocketHandler() {
		StompWebSocketHandler handler = new StompWebSocketHandler(inboundChannel());
		handler.setUserSessionResolver(this.userSessionResolver);
		outboundChannel().subscribe(handler);
		return handler;
	}

	// MessageHandler for delegating messages to annotated methods.

	@Bean
	public AnnotationMethodMessageHandler annotationMessageHandler() {

		AnnotationMethodMessageHandler handler =
				new AnnotationMethodMessageHandler(inboundMessagingTemplate(), outboundMessagingTemplate());

		handler.setMessageConverter(this.messageConverter);
		inboundChannel().subscribe(handler);
		return handler;
	}

	// MessageHandler that can serve as a simple message broker.

	@Bean
	@Profile("simple-broker")
	public SimpleBrokerMessageHandler simpleBrokerMessageHandler() {
		SimpleBrokerMessageHandler handler = new SimpleBrokerMessageHandler(outboundChannel());
		inboundChannel().subscribe(handler);
		return handler;
	}

	// MessageHandler that can relay messages to and from an external STOMP message broker.

	@Bean
	@Profile("stomp-broker-relay")
	public StompBrokerRelayMessageHandler stompBrokerRelayMessageHandler() {
		List<String> destinations = Arrays.asList("/topic", "/queue");
		StompBrokerRelayMessageHandler handler = new StompBrokerRelayMessageHandler(outboundChannel(), destinations);
		inboundChannel().subscribe(handler);
		return handler;
	}

	// MessageHandler that can resolve destinations prefixed with "/user/{user}"

	@Bean
	public UserDestinationMessageHandler userMessageHandler() {
		UserDestinationMessageHandler handler = new UserDestinationMessageHandler(inboundMessagingTemplate());
		handler.setUserSessionResolver(this.userSessionResolver);
		inboundChannel().subscribe(handler);
		return handler;
	}

	// MessagingTemplate and MessageChannel for incoming messages to the application

	@Bean
	public SimpMessageSendingOperations inboundMessagingTemplate() {
		SimpMessagingTemplate template = new SimpMessagingTemplate(inboundChannel());
		template.setMessageConverter(this.messageConverter);
		return template;
	}

	@Bean
	public SubscribableChannel inboundChannel() {
		return new ExecutorSubscribableChannel(asyncExecutor());
	}

	// MessagingTemplate and MessageChannel for outgoing messages to clients

	@Bean
	public SimpMessageSendingOperations outboundMessagingTemplate() {
		SimpMessagingTemplate template = new SimpMessagingTemplate(outboundChannel());
		template.setMessageConverter(this.messageConverter);
		return template;
	}

	@Bean
	public SubscribableChannel outboundChannel() {
		return new ExecutorSubscribableChannel(asyncExecutor());
	}

	// Executor for message passing via MessageChannel

	@Bean
	public ThreadPoolTaskExecutor asyncExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setCorePoolSize(8);
		executor.setThreadNamePrefix("MessageChannel-");
		return executor;
	}

	// Task executor for use in SockJS (heartbeat frames, session timeouts)

	@Bean
	public ThreadPoolTaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
		taskScheduler.setThreadNamePrefix("SockJS-");
		taskScheduler.setPoolSize(4);
		return taskScheduler;
	}

	// Allow serving HTML files through the default Servlet

	@Override
	public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
		configurer.enable();
	}

}
