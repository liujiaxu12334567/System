package com.project.system.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.system.entity.CourseChat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class RedisClassroomChatStore implements ClassroomChatStore {
    private static final int MAX_CHAT_PER_COURSE = 200;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static String listKey(Long courseId) {
        return "classroom:chat:list:" + courseId;
    }

    @Override
    public void append(CourseChat chat) {
        if (chat == null || chat.getCourseId() == null) return;
        try {
            String key = listKey(chat.getCourseId());
            String json = objectMapper.writeValueAsString(chat);
            redisTemplate.opsForList().leftPush(key, json);
            redisTemplate.opsForList().trim(key, 0, MAX_CHAT_PER_COURSE - 1);
        } catch (Exception ignored) {}
    }

    @Override
    public List<CourseChat> list(Long courseId, int limit) {
        if (courseId == null) return Collections.emptyList();
        int take = limit <= 0 ? MAX_CHAT_PER_COURSE : Math.min(limit, MAX_CHAT_PER_COURSE);
        try {
            List<String> raw = redisTemplate.opsForList().range(listKey(courseId), 0, take - 1);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            List<CourseChat> result = new ArrayList<>(raw.size());
            for (String s : raw) {
                if (s == null || s.isBlank()) continue;
                try {
                    result.add(objectMapper.readValue(s, CourseChat.class));
                } catch (Exception ignored) {}
            }
            Collections.reverse(result); // convert newest-first to chronological
            return result;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    @Override
    public void clear(Long courseId) {
        if (courseId == null) return;
        try {
            redisTemplate.delete(listKey(courseId));
            redisTemplate.delete("classroom:chat:seq:" + courseId);
        } catch (Exception ignored) {}
    }
}

