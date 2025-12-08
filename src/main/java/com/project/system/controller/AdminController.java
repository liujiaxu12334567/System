package com.project.system.controller;

import com.project.system.entity.User;
import com.project.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 1. 获取用户列表
    @GetMapping("/user/list")
    public ResponseEntity<?> listUsers(@RequestParam(required = false) String keyword) {
        List<User> list = userMapper.selectAllUsers(keyword);
        return ResponseEntity.ok(list);
    }

    // 2. 新增用户 (管理员直接添加)
    @PostMapping("/user/add")
    public ResponseEntity<?> addUser(@RequestBody User user) {
        if (userMapper.findByUsername(user.getUsername()) != null) {
            return ResponseEntity.badRequest().body("用户名已存在");
        }
        // 默认密码 123456
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            user.setPassword("123456");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        return ResponseEntity.ok("添加成功");
    }

    // 3. 更新用户 (核心功能：任命组长/修改密码)
    @PostMapping("/user/update")
    public ResponseEntity<?> updateUser(@RequestBody User user) {
        // 如果修改了密码，需要加密
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null); // 避免覆盖原密码
        }

        userMapper.updateUser(user);
        return ResponseEntity.ok("更新成功");
    }

    // 4. 删除用户
    @PostMapping("/user/delete/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        userMapper.deleteUserById(id);
        return ResponseEntity.ok("删除成功");
    }
}