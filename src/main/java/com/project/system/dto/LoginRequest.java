package com.project.system.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
    /**
     * 登录入口类型：teacher / student（管理员两端都允许）。
     * 为空则兼容旧客户端：不做入口限制。
     */
    private String loginType;
}
