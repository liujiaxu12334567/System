package com.project.system.controller;

import com.project.system.entity.Course;
import com.project.system.entity.User;
import com.project.system.mapper.CourseMapper;
import com.project.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leader")
public class LeaderController {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private UserMapper userMapper;

    // 1. 获取所有课程列表
    @GetMapping("/course/list")
    public ResponseEntity<?> listCourses() {
        return ResponseEntity.ok(courseMapper.selectAllCourses());
    }

    // 2. 获取所有可选教师 (Role=3)
    @GetMapping("/teacher/list")
    public ResponseEntity<?> listTeachers() {
        // 假设数据库里 3 代表普通教师
        List<User> teachers = userMapper.selectUsersByRole("3");
        return ResponseEntity.ok(teachers);
    }

    // 3. 发布新课程
    @PostMapping("/course/add")
    public ResponseEntity<?> addCourse(@RequestBody Course course) {
        // 生成一个随机课程代码 (模拟)
        course.setCode("C" + System.currentTimeMillis() % 10000);
        course.setStatus("进行中");
        course.setColor("blue"); // 默认颜色
        courseMapper.insertCourse(course);
        return ResponseEntity.ok("课程发布成功");
    }

    // 4. 分配教师 / 更新课程
    @PostMapping("/course/update")
    public ResponseEntity<?> updateCourse(@RequestBody Course course) {
        courseMapper.updateCourse(course);
        return ResponseEntity.ok("更新成功");
    }

    // 5. 删除课程
    @PostMapping("/course/delete/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        courseMapper.deleteCourseById(id);
        return ResponseEntity.ok("删除成功");
    }
}