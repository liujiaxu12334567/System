package com.project.system.service.impl;

import com.project.system.entity.Course;
import com.project.system.entity.Notification;
import com.project.system.entity.User;
import com.project.system.mapper.*;
import com.project.system.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable; // Redis 缓存注解
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StudentServiceImpl implements StudentService {

    @Autowired private CourseMapper courseMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private NotificationMapper notificationMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ★★★ Redis 缓存：缓存课程详情，key 为 courseId ★★★
    @Override
    @Cacheable(value = "course_info", key = "#courseId")
    public Object getCourseInfo(Long courseId) {
        System.out.println("【Redis】未命中缓存，正在查询数据库 courseId: " + courseId);
        List<Course> all = courseMapper.selectAllCourses();
        return all.stream().filter(c -> c.getId().equals(courseId)).findFirst().orElse(null);
    }

    @Override
    public Object getRecentActivities() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);
        if (user == null) return new ArrayList<>();

        List<Notification> notifications = notificationMapper.selectByUserId(user.getUserId());

        return notifications.stream().map(n -> {
            Map<String, Object> map = new HashMap<>();
            map.put("type", n.getType());
            map.put("title", n.getTitle());
            map.put("message", n.getMessage());
            map.put("time", n.getCreateTime().format(FORMATTER));
            map.put("displayTime", n.getCreateTime().format(FORMATTER));
            return map;
        }).collect(Collectors.toList());
    }

    // ... 其他方法的实现 ...
}