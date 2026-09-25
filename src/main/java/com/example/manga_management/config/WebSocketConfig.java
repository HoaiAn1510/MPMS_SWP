package com.example.manga_management.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatHandshakeInterceptor chatHandshakeInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(ChatHandshakeInterceptor chatHandshakeInterceptor,
            @Value("${app.websocket.allowed-origins:}") String[] allowedOrigins) {
        this.chatHandshakeInterceptor = chatHandshakeInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // /ws-chat được miễn CSRF token nên dựa vào kiểm tra Origin: mặc định chỉ cho phép
        // cùng origin. Nếu chạy sau proxy/domain khác, khai báo APP_WS_ALLOWED_ORIGINS.
        var endpoint = registry.addEndpoint("/ws-chat")
                .addInterceptors(chatHandshakeInterceptor)
                .setHandshakeHandler(new ChatHandshakeHandler());
        if (allowedOrigins.length > 0 && !allowedOrigins[0].isBlank()) {
            endpoint.setAllowedOriginPatterns(allowedOrigins);
        }
        endpoint.withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
}
