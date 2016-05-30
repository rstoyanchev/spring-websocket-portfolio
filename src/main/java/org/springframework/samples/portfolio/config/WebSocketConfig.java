package org.springframework.samples.portfolio.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.samples.portfolio.security.PortfolioAuthentication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

@Configuration
@EnableScheduling
@ComponentScan("org.springframework.samples")
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/portfolio")
                .addInterceptors(new HttpSessionHandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
                        super.beforeHandshake(request, response, wsHandler, attributes);
                        final String address = request.getRemoteAddress().getAddress().getHostAddress();
                        log.info("A user is trying to connect from " + address);
                        final Map<String, String[]> parameterMap = ((ServletServerHttpRequest) request).getServletRequest().getParameterMap();
                        final String[] access_tokens = parameterMap.get("access_token");
                        if (null != access_tokens && access_tokens.length > 0) {
                            attributes.put("token", access_tokens[0]);
                            return true;
                        }
                        response.setStatusCode(HttpStatus.FORBIDDEN);
                        response.flush();
                        response.close();
                        return false;
                    }
                })
                .setHandshakeHandler(new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
                final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                final String ip = request.getRemoteAddress().getAddress().getHostAddress();
                return new PortfolioAuthentication(authentication.getPrincipal(), authentication.getCredentials(),
                        (String) attributes.get("token"), ip);
            }
        })
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
//		registry.enableSimpleBroker("/queue/", "/topic/");
        registry.enableStompBrokerRelay("/topic/");
        registry.setApplicationDestinationPrefixes("/app");
    }

}
