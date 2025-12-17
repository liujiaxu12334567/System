package com.project.system.config;

import com.project.system.websocket.ClassroomWebSocketHandler;
import com.project.system.websocket.ClassroomHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private ClassroomWebSocketHandler classroomWebSocketHandler;

    @Autowired
    private ClassroomHandshakeInterceptor classroomHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(classroomWebSocketHandler, "/ws/classroom")
                .setAllowedOrigins("*")
                .addInterceptors(classroomHandshakeInterceptor)
                .withSockJS()
                .setInterceptors(classroomHandshakeInterceptor);
    }
}
