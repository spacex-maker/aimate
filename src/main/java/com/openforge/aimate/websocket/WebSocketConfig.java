package com.openforge.aimate.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Spring STOMP/WebSocket configuration.
 *
 * 链路（与前端 useAgentSocket 对应）：
 *   1. 前端 SockJS(/ws) 握手 → 本端 /ws（SecurityConfig 放行 /ws/**）
 *   2. 前端 STOMP CONNECT（可带 Authorization header）→ CONNECTED
 *   3. 前端 SUBSCRIBE destination=/topic/agent/{sessionId}
 *   4. 后端 Agent 循环里 AgentEventPublisher.publish(event) → convertAndSend("/topic/agent/"+sessionId, event)
 *   5. 前端在 subscribe 回调里收 MESSAGE frame，body 为 AgentEvent JSON
 *
 * 注意：若前端连接/订阅晚于 Agent 开始（如先 POST 创建会话再跳转），会漏事件；
 * 前端 onConnected 时需用 HTTP 拉会话+消息做兜底。
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
