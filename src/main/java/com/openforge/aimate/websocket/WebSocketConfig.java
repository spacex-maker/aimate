package com.openforge.aimate.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Spring STOMP/WebSocket configuration.
 *
 * Frontend connection flow:
 *   1. Connect to  ws://host/ws  (or SockJS fallback: http://host/ws)
 *   2. STOMP SUBSCRIBE /topic/agent/{sessionId}
 *   3. Receive AgentEvent JSON frames in real-time as the Agent thinks
 *
 * Topic design:
 *   /topic/agent/{sessionId}  — broadcast stream for a single Agent session
 *
 * The in-memory simple broker is sufficient for single-node deployments.
 * For multi-node, replace with a RabbitMQ/Redis relay broker.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory pub-sub broker, handles /topic/** destinations
        registry.enableSimpleBroker("/topic");
        // Prefix for @MessageMapping methods (inbound from client, not used yet)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // Allow all origins for dev; tighten in production
                .setAllowedOriginPatterns("*")
                // SockJS fallback for environments that block raw WebSocket
                .withSockJS();
    }
}
