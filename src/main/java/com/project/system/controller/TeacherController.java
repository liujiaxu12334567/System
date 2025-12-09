package com.project.system.controller;

import com.project.system.dto.PaginationResponse; // 【修复】导入 DTO
import com.project.system.entity.Application;
import com.project.system.entity.User;
import com.project.system.mapper.ApplicationMapper;
import com.project.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ApplicationMapper applicationMapper;

    // 1. 获取该老师执教班级的学生列表 (支持分页和筛选)
    @GetMapping("/students")
    public ResponseEntity<?> listStudents(
            @RequestParam(required = false) String keyword, // 模糊查询关键字
            @RequestParam(required = false) String classId, // 班级ID筛选
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {

        // 1. 获取当前登录老师
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        // 2. 获取该老师执教的班级 (字符串转List)
        String classesStr = teacher.getTeachingClasses();
        if (classesStr == null || classesStr.isEmpty()) {
            // 没有分配班级，直接返回空列表
            return ResponseEntity.ok(new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize));
        }

        // 3. 解析并清理班级ID列表
        List<String> validClassIds = Arrays.stream(classesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (validClassIds.isEmpty()) {
            return ResponseEntity.ok(new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize));
        }

        // 4. 准备查询参数
        int offset = (pageNum - 1) * pageSize;

        // 如果前端也传入了 classId 筛选，确保它在老师的执教范围内
        String finalClassId = null;
        if (classId != null && !classId.isEmpty()) {
            // 检查前端传入的 classId 是否在老师的执教范围内
            if (!validClassIds.contains(classId)) {
                // 教师筛选了非执教班级，返回空
                return ResponseEntity.ok(new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize));
            }
            finalClassId = classId;
        }

        // 5. 调用 Mapper 获取总数和分页列表
        // 注意：UserMapper 中需要有 countStudentsByTeachingClasses 和 selectStudentsByTeachingClasses 方法
        long total = userMapper.countStudentsByTeachingClasses(keyword, finalClassId, validClassIds);

        if (total == 0) {
            return ResponseEntity.ok(new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize));
        }

        List<User> list = userMapper.selectStudentsByTeachingClasses(keyword, finalClassId, validClassIds, offset, pageSize);

        // 6. 返回分页结果
        return ResponseEntity.ok(new PaginationResponse<>(list, total, pageNum, pageSize));
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