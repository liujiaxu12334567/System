package com.project.system.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.system.entity.CourseChat;
import com.project.system.service.support.ClassroomMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RedisClassroomEventSubscriber {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClassroomWebSocketHandler classroomWebSocketHandler;

    @Autowired
    private ClassroomMemoryStore classroomMemoryStore;

    public void onMessage(String message) {
        if (message == null || message.isBlank()) return;
        try {
            JsonNode node = objectMapper.readTree(message);
            long courseId = node.path("courseId").asLong(0);
            if (courseId <= 0) return;

            String type = node.path("type").asText("");
            if ("chat".equals(type)) {
                CourseChat chat = objectMapper.treeToValue(node.path("payload"), CourseChat.class);
                classroomMemoryStore.appendChat(chat);
            } else if ("reset".equals(type) || "end".equals(type)) {
                classroomMemoryStore.clearCourse(courseId);
            }

            classroomWebSocketHandler.broadcast(courseId, message);
        } catch (Exception ignored) {}
    }
}

