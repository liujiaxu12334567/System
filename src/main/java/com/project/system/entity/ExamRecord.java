package com.project.system.entity;

import lombok.Data;
import java.time.LocalDateTime;

// 考试记录表 (包含切屏次数)
@Data
public class ExamRecord {
    private Long id;
    private Long userId;
    private Long examId;
    private Integer score;
    private String userAnswers;         // 存储 JSON 字符串
    private Integer cheatCount;         // 【新增】切屏次数
    private LocalDateTime submitTime;
}