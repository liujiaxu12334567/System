package com.project.system.service.support;

import com.project.system.entity.CourseChat;

import java.util.List;

public interface ClassroomChatStore {
    void append(CourseChat chat);

    List<CourseChat> list(Long courseId, int limit);

    void clear(Long courseId);
}

