package com.project.system.service;

import org.springframework.http.ResponseEntity;

public interface StudentService {
    Object getCourseInfo(Long courseId);
    Object getRecentActivities();
    // ... 其他方法定义
}