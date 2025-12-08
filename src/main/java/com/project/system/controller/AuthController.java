package com.project.system.controller;

import com.project.system.dto.LoginRequest;
import com.project.system.dto.LoginResponse;
import com.project.system.entity.User;
import com.project.system.mapper.UserMapper;
import com.project.system.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder; // 1. 导入 PasswordEncoder
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

    // 2. 注入密码加密器 (非常重要，注册时密码必须加密)
    @Autowired
    PasswordEncoder passwordEncoder;
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {

        // 1. 【关键判断】检查数据库是否已有该学号
        if (userMapper.findByUsername(user.getUsername()) != null) {
            // 返回 400 状态码，并附带具体的错误提示文字
            return ResponseEntity.badRequest().body("注册失败：该学号/工号 (" + user.getUsername() + ") 已存在！");
        }

        // 2. 密码加密
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 3. 写入数据库
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
}