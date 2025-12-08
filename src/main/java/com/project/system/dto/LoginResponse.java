package com.project.system.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private Long id;
    private String username;
    private String realName;
    private String role;
    private String teacherRank;

    public LoginResponse(String token, Long id, String username, String realName, String role, String teacherRank) {
        this.token = token;
        this.id = id;
        this.username = username;
        this.realName = realName;
        this.role = role;
        this.teacherRank = teacherRank;
    }
}