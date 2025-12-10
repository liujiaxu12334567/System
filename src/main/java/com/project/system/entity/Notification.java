package com.project.system.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Notification {
    private Long id;
    private Long userId;        // 目标用户ID (学生或教师)
    private Long relatedId;     // 关联的业务ID (如 materialId, examId)
    private String type;        // 通知类型: GRADE_SUCCESS, REJECT_SUBMISSION, PUBLISH_NEW, DEADLINE_EXTENDED, GENERAL_NOTICE
    private String title;       // 通知标题
    private String message;     // 通知内容详情
    private Boolean isRead;     // 是否已读
    private LocalDateTime createTime;
}