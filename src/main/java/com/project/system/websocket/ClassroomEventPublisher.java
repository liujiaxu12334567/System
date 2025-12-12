package com.project.system.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClassroomEventPublisher {

    @Autowired
    private ClassroomWebSocketHandler handler;

    private final ObjectMapper mapper = new ObjectMapper();

    public void publish(ClassroomEvent event) {
        if (event == null || event.getCourseId() == null) return;
        try {
            String json = mapper.writeValueAsString(event);
            handler.broadcast(event.getCourseId(), json);
        } catch (Exception ignored) {}
    }
}
