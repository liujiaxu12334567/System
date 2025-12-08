package com.project.system.entity;

import lombok.Data;

@Data
public class Task {
    private Long id;
    private String name;
    private String courseName; // 对应数据库 course_name
    private String deadline;
    private String status;
}