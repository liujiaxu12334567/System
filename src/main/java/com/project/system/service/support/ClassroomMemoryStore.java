package com.project.system.service.support;

import com.project.system.entity.CourseChat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 课堂内存态存储（仅用于本次会话，避免聊天内容入库）
 */
@Component
public class ClassroomMemoryStore {
    private static final int MAX_CHAT_PER_COURSE = 200;
    private final Map<Long, Deque<CourseChat>> chatMap = new ConcurrentHashMap<>();
    private final AtomicLong chatSeq = new AtomicLong(1L);

    public CourseChat appendChat(CourseChat chat) {
        if (chat == null || chat.getCourseId() == null) return chat;
        if (chat.getId() == null) chat.setId(chatSeq.getAndIncrement());
        if (chat.getCreateTime() == null) chat.setCreateTime(LocalDateTime.now());
        Deque<CourseChat> deque = chatMap.computeIfAbsent(chat.getCourseId(), k -> new ConcurrentLinkedDeque<>());
        deque.addLast(chat);
        while (deque.size() > MAX_CHAT_PER_COURSE) {
            deque.pollFirst();
        }
        return chat;
    }

    public List<CourseChat> listChats(Long courseId, int limit) {
        if (courseId == null) return List.of();
        Deque<CourseChat> deque = chatMap.get(courseId);
        if (deque == null || deque.isEmpty()) return List.of();
        List<CourseChat> list = new ArrayList<>(deque);
        if (limit > 0 && list.size() > limit) {
            return list.subList(list.size() - limit, list.size());
        }
        return list;
    }

    public void clearCourse(Long courseId) {
        if (courseId == null) return;
        chatMap.remove(courseId);
    }
}
