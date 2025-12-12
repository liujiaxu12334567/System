package com.project.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseChat {
    private Long id;
    private Long courseId;
    private Long senderId;
    private String senderName;
    private String role; // teacher/student
    private String content;
    private LocalDateTime createTime;
}
