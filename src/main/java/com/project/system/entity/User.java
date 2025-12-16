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
    private String roleType;    // 1=Admin, 2=Leader, 3=Teacher, 4=Student
    private String teacherRank;
    private String accountStatus;

    // 【新增】教师所属学院（用于分类筛选）
    private String college;

    private Long classId;           // 【学生用】所属班级
    private String teachingClasses; // 【教师用】执教班级 (例如 "101,102")

    private LocalDateTime createTime;
}
