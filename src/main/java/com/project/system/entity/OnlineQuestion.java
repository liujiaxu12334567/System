package com.project.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OnlineQuestion {
    private Long id;
    private Long courseId;
    private Long classId;
    private Long teacherId;
    private String title;
    private String content;
    private String correctAnswer;
    private LocalDateTime deadline;
    private LocalDateTime createTime;

    // 非持久化字段：模式/描述解析
    private String mode;       // broadcast/hand/race/assign
    private String description;
    private Long assignStudentId; // mode=assign 时有效
}
