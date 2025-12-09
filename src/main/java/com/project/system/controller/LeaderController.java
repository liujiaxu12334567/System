package com.project.system.controller;

import com.project.system.entity.User;
import com.project.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leader")
public class LeaderController {

    @Autowired
    private UserMapper userMapper;

    // 1. 获取所有可选教师 (Role=3) - 仍保留此接口，方便 Leader 查看团队成员
    @GetMapping("/teacher/list")
    public ResponseEntity<?> listTeachers() {
        // 假设数据库里 3 代表普通教师
        List<User> teachers = userMapper.selectUsersByRole("3");
        return ResponseEntity.ok(teachers);
    }

    // TODO: Leader Controller 接下来应聚焦于审核（ApplicationMapper）和团队数据统计
    // 课程管理API已全部移至 AdminController
}