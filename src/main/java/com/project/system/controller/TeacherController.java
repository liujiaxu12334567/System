package com.project.system.controller;

import com.project.system.entity.Application;
import com.project.system.entity.User;
import com.project.system.mapper.ApplicationMapper; // 假设你已创建此 Mapper
import com.project.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ApplicationMapper applicationMapper; // 假设你已创建此 Mapper

    // 1. 【核心逻辑】获取该老师执教班级的学生列表
    @GetMapping("/students")
    public ResponseEntity<?> listStudents() {
        // 1. 获取当前登录老师
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        // 2. 获取该老师执教的班级 (字符串转List)
        String classesStr = teacher.getTeachingClasses();
        if (classesStr == null || classesStr.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList()); // 没分配班级，返回空列表
        }

        // 3. 解析班级ID "101,102" -> [101, 102]
        List<Long> classIds = new ArrayList<>();
        try {
            String[] split = classesStr.split(",");
            for (String s : split) {
                // 确保数据干净，防止空格影响转换
                classIds.add(Long.parseLong(s.trim()));
            }
        } catch (Exception e) {
            // 如果 teaching_classes 格式不对，返回错误提示
            return ResponseEntity.badRequest().body("系统错误：教师执教班级配置格式错误，请联系管理员");
        }

        // 4. 查询这些班级的学生
        // 注意：这需要 UserMapper.xml 中有 selectStudentsByClassIds 方法
        List<User> students = userMapper.selectStudentsByClassIds(classIds);
        return ResponseEntity.ok(students);
    }

    // 2. 提交申请 (核心功能：增/删/改学生信息)
    @PostMapping("/apply")
    public ResponseEntity<?> submitApplication(@RequestBody Application app) {
        // 获取当前登录教师信息
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        app.setTeacherId(teacher.getUserId());
        app.setTeacherName(teacher.getRealName());

        if (app.getType() == null || app.getReason() == null) {
            return ResponseEntity.badRequest().body("申请信息不完整，请填写类型和理由。");
        }

        // 插入申请记录到 sys_application 表，状态默认为 PENDING
        applicationMapper.insert(app);
        return ResponseEntity.ok("学生管理申请已提交，请等待组长/管理员审核。");
    }

    // 3. 查看我的申请记录
    @GetMapping("/my-applications")
    public ResponseEntity<?> myApplications() {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        List<Application> list = applicationMapper.findByTeacherId(teacher.getUserId());
        return ResponseEntity.ok(list);
    }
}