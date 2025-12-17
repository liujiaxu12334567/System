package com.project.system.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ClassroomWebSocketHandler extends TextWebSocketHandler {

    // courseId -> sessions
    private final Map<Long, Set<WebSocketSession>> courseSessions = new ConcurrentHashMap<>();
    private final ClassroomOnlineUserStore onlineUserStore;

    public ClassroomWebSocketHandler(ClassroomOnlineUserStore onlineUserStore) {
        this.onlineUserStore = onlineUserStore;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long courseId = extractCourseId(session.getUri());
        if (courseId != null) {
            courseSessions.computeIfAbsent(courseId, k -> ConcurrentHashMap.newKeySet()).add(session);
            session.getAttributes().put("courseId", courseId);

            Object uid = session.getAttributes().get("userId");
            if (uid instanceof Long) {
                onlineUserStore.connect(courseId, (Long) uid);
            }
        } else {
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Object cid = session.getAttributes().get("courseId");
        Object uid = session.getAttributes().get("userId");
        if (cid instanceof Long) {
            Set<WebSocketSession> set = courseSessions.get((Long) cid);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    courseSessions.remove((Long) cid);
                }
            }
            if (uid instanceof Long) {
                onlineUserStore.disconnect((Long) cid, (Long) uid);
            }
        }
    }

    public void broadcast(Long courseId, String payload) {
        if (courseId == null) return;
        Set<WebSocketSession> sessions = courseSessions.get(courseId);
        if (sessions == null || sessions.isEmpty()) return;
        List<WebSocketSession> toRemove = new ArrayList<>();
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) {
                toRemove.add(s);
                continue;
            }
            try {
                s.sendMessage(new TextMessage(payload));
            } catch (Exception e) {
                toRemove.add(s);
            }
        }
        sessions.removeAll(toRemove);
    }

    private Long extractCourseId(URI uri) {
        if (uri == null || uri.getQuery() == null) return null;
        String[] parts = uri.getQuery().split("&");
        for (String p : parts) {
            String[] kv = p.split("=");
            if (kv.length == 2 && "courseId".equals(kv[0])) {
                try {
                    return Long.parseLong(kv[1]);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
