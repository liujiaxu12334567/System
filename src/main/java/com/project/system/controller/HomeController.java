package com.project.system.controller;

import com.project.system.entity.Course;
import com.project.system.entity.Task;
import com.project.system.entity.User;
import com.project.system.mapper.HomeMapper;
import com.project.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    @Autowired
    private HomeMapper homeMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/data")
    public ResponseEntity<?> getHomeData() {
        // 1. 获取当前登录用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        // 2. 查询真实姓名
        String realName = "同学";
        if (username != null) {
            User user = userMapper.findByUsername(username);
            if (user != null && user.getRealName() != null) {
                realName = user.getRealName();
            }
        }

        // 3. 查询课程和任务
        List<Course> courses = homeMapper.selectAllCourses();
        List<Task> tasks = homeMapper.selectAllTasks();

        // 4. 封装返回
        Map<String, Object> result = new HashMap<>();
        result.put("realName", realName);
        result.put("courses", courses);
        result.put("tasks", tasks);

        return ResponseEntity.ok(result);
    }
}