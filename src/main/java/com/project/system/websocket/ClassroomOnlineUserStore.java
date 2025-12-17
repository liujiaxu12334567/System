package com.project.system.websocket;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ClassroomOnlineUserStore {

    // courseId -> (userId -> sessionCount)
    private final Map<Long, Map<Long, AtomicInteger>> courseUserCounts = new ConcurrentHashMap<>();

    public void connect(Long courseId, Long userId) {
        if (courseId == null || userId == null) return;
        Map<Long, AtomicInteger> counts = courseUserCounts.computeIfAbsent(courseId, k -> new ConcurrentHashMap<>());
        counts.compute(userId, (k, v) -> {
            if (v == null) return new AtomicInteger(1);
            v.incrementAndGet();
            return v;
        });
    }

    public void disconnect(Long courseId, Long userId) {
        if (courseId == null || userId == null) return;
        Map<Long, AtomicInteger> counts = courseUserCounts.get(courseId);
        if (counts == null) return;
        counts.computeIfPresent(userId, (k, v) -> (v.decrementAndGet() <= 0) ? null : v);
        if (counts.isEmpty()) {
            courseUserCounts.remove(courseId);
        }
    }

    public Set<Long> getOnlineUserIds(Long courseId) {
        if (courseId == null) return Collections.emptySet();
        Map<Long, AtomicInteger> counts = courseUserCounts.get(courseId);
        if (counts == null || counts.isEmpty()) return Collections.emptySet();
        return counts.keySet();
    }
}

