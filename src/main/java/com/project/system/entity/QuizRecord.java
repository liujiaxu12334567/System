package com.project.system.entity;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QuizRecord {
    private Long id;
    private Long userId;
    private Long materialId;
    private Integer score;
    private String userAnswers; // 存储 JSON 字符串
    private LocalDateTime submitTime;
}