package org.springframework.samples.portfolio.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.EnableWebSocketMessageBroker;
import org.springframework.messaging.simp.config.MessageBrokerConfigurer;
import org.springframework.messaging.simp.config.StompEndpointRegistry;
import org.springframework.messaging.simp.config.WebSocketMessageBrokerConfigurer;
import org.springframework.scheduling.annotation.EnableScheduling;


@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
@ComponentScan(basePackages="org.springframework.samples")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/portfolio").withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerConfigurer configurer) {
		configurer.enableSimpleBroker("/queue/", "/topic/");
//		configurer.enableStompBrokerRelay("/queue/", "/topic/");
		configurer.setAnnotationMethodDestinationPrefixes("/app");
	}

}
