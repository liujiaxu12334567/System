package com.project.system.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long userId;
    private String username;
    private String password;
    private String realName;
    private String email;
    private String roleType;    // ADMIN, TEACHER, STUDENT
    private String teacherRank; // NORMAL, LEADER
    private String accountStatus;
    private Long classId;       // 新增：班级ID
    private LocalDateTime createTime;
}