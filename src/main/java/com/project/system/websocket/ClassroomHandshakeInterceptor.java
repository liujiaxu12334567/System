package com.project.system.websocket;

import com.project.system.entity.User;
import com.project.system.mapper.UserMapper;
import com.project.system.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Component
public class ClassroomHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserMapper userMapper;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String token = getQueryParam(uri, "token");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length());
        }
        if (token != null && !token.isBlank() && jwtUtils.validateJwtToken(token)) {
            String username = jwtUtils.getUserNameFromJwtToken(token);
            User u = username == null ? null : userMapper.findByUsername(username);
            if (u != null && u.getUserId() != null) {
                attributes.put("userId", u.getUserId());
                attributes.put("roleType", u.getRoleType());
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String getQueryParam(URI uri, String key) {
        if (uri == null || uri.getQuery() == null || key == null || key.isBlank()) return null;
        String[] parts = uri.getQuery().split("&");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}

