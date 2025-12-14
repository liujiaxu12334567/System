package com.project.system.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

@Component
public class RedisClassroomEventBus implements ClassroomEventBus {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ChannelTopic classroomEventsTopic;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void publish(ClassroomEvent event) {
        if (event == null || event.getCourseId() == null) return;
        try {
            redisTemplate.convertAndSend(classroomEventsTopic.getTopic(), objectMapper.writeValueAsString(event));
        } catch (Exception ignored) {}
    }
}

