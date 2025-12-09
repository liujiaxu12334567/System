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

import java.util.ArrayList;
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

        User user = null;
        String realName = "同学";

        if (username != null) {
            user = userMapper.findByUsername(username);
            if (user != null && user.getRealName() != null) {
                realName = user.getRealName();
            }
        }

        // 2. 查询课程 (根据角色筛选) [核心修改点]
        List<Course> courses = new ArrayList<>();

        if (user != null && "4".equals(user.getRoleType())) {
            // 如果是学生 (Role=4)，且已分班，只查询该班级的课程
            if (user.getClassId() != null) {
                courses = homeMapper.selectCoursesByClassId(user.getClassId());
            } else {
                // 如果学生还没分班，暂时不显示课程
                courses = new ArrayList<>();
            }
        } else {
            // 如果是管理员或其他角色，默认查看所有课程
            courses = homeMapper.selectAllCourses();
        }

        // 3. 查询任务 (这里也可以根据 user 做进一步筛选，目前保持不变)
        List<Task> tasks = homeMapper.selectAllTasks();

        // 4. 封装返回
        Map<String, Object> result = new HashMap<>();
        result.put("realName", realName);
        result.put("courses", courses);
        result.put("tasks", tasks);

        return ResponseEntity.ok(result);
    }
}