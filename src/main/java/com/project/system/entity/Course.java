package com.project.system.entity;

import lombok.Data;

@Data
public class Course {
    private Long id;
    private String name;
    private String semester;

    // 排课时间（课程表）
    private Integer dayOfWeek; // 1=周一...7=周日
    private String startTime;
    private String endTime;

    // 授课教师
    private String teacher;   // 展示：教师姓名
    private Long teacherId;   // 约束：sys_user.user_id（role=2/3）

    private String code;
    private String status;
    private String color;
    private Integer isTop;

    // 课程组信息
    private String managerName; // 展示：组长姓名
    private Long leaderId;      // 约束：课程组组长ID（sys_user.user_id）
    private Long groupId;       // 约束：课程组ID（sys_course_group.group_id）

    // 班级信息
    private Long classId;
    private String responsibleClassIds;

    // 运行时补充
    private Long studentCount;
}
