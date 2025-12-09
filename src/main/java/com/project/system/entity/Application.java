package com.project.system.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Application {
    private Long id;
    private Long teacherId;     // 申请人ID
    private String teacherName; // 申请人姓名
    private String type;        // 类型：ADD, DELETE, RESET_PWD
    private String content;     // 申请详情/内容描述（如：新增学生姓名张三）
    private Long targetId;      // 操作对象ID (学生ID)
    private String reason;      // 理由
    private String status;      // 状态：PENDING, APPROVED, REJECTED
    private LocalDateTime createTime;
}