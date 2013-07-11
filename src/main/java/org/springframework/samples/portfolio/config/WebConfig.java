package org.springframework.samples.portfolio.config;

import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.channel.PublishSubscribeChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.messaging.service.broker.SimpleBrokerWebMessageHandler;
import org.springframework.web.messaging.service.method.AnnotationWebMessageHandler;
import org.springframework.web.messaging.stomp.support.StompWebSocketHandler;
import org.springframework.web.messaging.support.WebMessagingTemplate;
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
	public AnnotationWebMessageHandler annotationMessageHandler() {
		AnnotationWebMessageHandler handler = new AnnotationWebMessageHandler(inboundChannel(), outboundChannel());
		handler.setMessageConverter(this.messageConverter);
		inboundChannel().subscribe(handler);
		return handler;
	}

	@Bean
	public SimpleBrokerWebMessageHandler simpleBrokerMessageHandler() {
		SimpleBrokerWebMessageHandler handler = new SimpleBrokerWebMessageHandler(outboundChannel());
		inboundChannel().subscribe(handler);
		return handler;
	}

	@Bean
	public SubscribableChannel inboundChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public SubscribableChannel outboundChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public WebMessagingTemplate messagingTemplate() {
		WebMessagingTemplate template = new WebMessagingTemplate(inboundChannel());
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
