package com.project.system.controller;

import com.project.system.dto.LoginRequest;
import com.project.system.dto.LoginResponse;
import com.project.system.dto.PasswordChangeRequest; // 【新增导入】
import com.project.system.entity.User;
import com.project.system.mapper.UserMapper;
import com.project.system.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserMapper userMapper;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {

        if (userMapper.findByUsername(user.getUsername()) != null) {
            return ResponseEntity.badRequest().body("注册失败：该学号/工号 (" + user.getUsername() + ") 已存在！");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        userMapper.insert(user);

        return ResponseEntity.ok("注册成功！");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = jwtUtils.generateToken(loginRequest.getUsername());

        User user = userMapper.findByUsername(loginRequest.getUsername());

        return ResponseEntity.ok(new LoginResponse(
                jwt,
                user.getUserId(),
                user.getUsername(),
                user.getRealName(),
                user.getRoleType(),
                user.getTeacherRank()
        ));
    }

    // 【新增接口】修改用户密码
    @PostMapping("/update-password")
    public ResponseEntity<?> updatePassword(@RequestBody PasswordChangeRequest request) {
        // 1. 获取当前登录用户的用户名
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // 2. 从数据库加载完整用户记录 (包含旧密码哈希值)
        User user = userMapper.findByUsername(currentUsername);

        if (user == null) {
            return ResponseEntity.status(404).body("用户不存在或登录信息失效");
        }

        // 3. 验证旧密码是否正确
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body("原密码输入错误");
        }

        // 4. 验证新密码是否为空
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body("新密码不能少于6位");
        }

        // 5. 加密新密码并更新数据库
        User userUpdate = new User();
        userUpdate.setUserId(user.getUserId());
        userUpdate.setPassword(passwordEncoder.encode(request.getNewPassword()));

        userMapper.updateUser(userUpdate); // 依赖 UserMapper 中的 updateUser 方法

        return ResponseEntity.ok("密码修改成功");
    }
}