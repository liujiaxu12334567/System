package com.project.system.entity;

import lombok.Data;

@Data
public class Course {
    private Long id;
    private String name;
    private String semester;

    private String teacher;     // 展示用：教师姓名
    private Long teacherId;     // 业务约束用：授课教师ID（sys_user.user_id）

    private String code;
    private String status;
    private String color;
    private Integer isTop;

    private String managerName; // 展示用：组长姓名
    private Long leaderId;      // 业务约束用：课程组组长ID（sys_user.user_id）
    private Long groupId;       // 课程组ID（sys_course_group.group_id）

    private Long classId;
    private String responsibleClassIds;
    private Long studentCount;  // 运行时填充
}

