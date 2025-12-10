package com.project.system.entity;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QuizRecord {
    private Long id;
    private Long userId;
    private Long materialId;
    private Integer score;
    private String userAnswers;

    // 【新增】存储AI的分析结果
    private String aiFeedback;

    private LocalDateTime submitTime;
}