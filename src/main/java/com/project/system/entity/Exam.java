package com.project.system.entity;

import lombok.Data;
import java.time.LocalDateTime;

// 考试发布记录表 (与 Material 相似)
@Data
public class Exam {
    private Long id;
    private Long courseId;
    private String title;       // 考试标题
    private String content;     // 试题内容 (JSON 字符串，格式与测验的 questions 相同)
    private String status;      // 状态：未开始, 进行中, 已结束
    private String startTime;   // 【新增】考试开始时间
    private String endTime;     // 【修改】考试结束/截止时间
    private Integer duration;   // 考试时长（分钟）
    private Long teacherId;     // 【新增】发布教师ID
    private LocalDateTime createTime;
}