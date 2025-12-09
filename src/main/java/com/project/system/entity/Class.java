package com.project.system.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Class {
    private Long classId; // 班级 ID (主键)
    private String className; // 班级名称
    private String major; // 【新增】专业字段
    private LocalDateTime createTime;

    public Class() {
        this.createTime = LocalDateTime.now();
    }

    // 【修改】新增 major 参数
    public Class(Long classId, String className, String major) {
        this.classId = classId;
        this.className = className;
        this.major = major;
        this.createTime = LocalDateTime.now();
    }
}