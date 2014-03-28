package org.springframework.samples.portfolio.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.List;


@Configuration
@EnableScheduling
@ComponentScan(
		basePackages="org.springframework.samples",
		excludeFilters = @ComponentScan.Filter(type= FilterType.ANNOTATION, value = Configuration.class)
)
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

	@Autowired Environment env;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {

		if (env.acceptsProfiles("test.tomcat")) {
			// Since test classpath includes both embedded Tomcat and Jetty, use profile to decide
			registry.addEndpoint("/portfolio").setHandshakeHandler(
					new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy())).withSockJS();
		}
		else {
			registry.addEndpoint("/portfolio").withSockJS();
		}
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/queue/", "/topic/");
//		registry.enableStompBrokerRelay("/queue/", "/topic/");
		registry.setApplicationDestinationPrefixes("/app");
	}

}
