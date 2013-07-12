package org.springframework.samples.portfolio.config;

import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.handler.AnnotationSimpMessageHandler;
import org.springframework.messaging.simp.handler.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompWebSocketHandler;
import org.springframework.messaging.support.channel.PublishSubscribeChannel;
import org.springframework.messaging.support.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.support.converter.MessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
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
		outboundChannel().subscribe(handler);
		return handler;
	}

	@Bean
	public AnnotationSimpMessageHandler annotationMessageHandler() {
		AnnotationSimpMessageHandler handler = new AnnotationSimpMessageHandler(outboundChannel());
		handler.setMessageConverter(this.messageConverter);
		inboundChannel().subscribe(handler);
		return handler;
	}

	@Bean
	public SimpleBrokerMessageHandler simpleBrokerMessageHandler() {
		SimpleBrokerMessageHandler handler = new SimpleBrokerMessageHandler(outboundChannel());
		inboundChannel().subscribe(handler);
		return handler;
	}

	@Bean
	public SubscribableChannel inboundChannel() {
		// TODO: executor
		return new PublishSubscribeChannel();
	}

	@Bean
	public SubscribableChannel outboundChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public SimpMessagingTemplate messagingTemplate() {
		SimpMessagingTemplate template = new SimpMessagingTemplate(inboundChannel());
		template.setMessageConverter(this.messageConverter);
		return template;
	}


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
