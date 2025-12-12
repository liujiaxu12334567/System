package com.project.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OnlineAnswer {
    private Long id;
    private Long questionId;
    private Long studentId;
    private String answerText;
    private LocalDateTime createTime;

    // 非持久化字段：解析类型/状态/文本
    private String type;   // answer/hand/race/speak
    private String state;  // pending/called/answered
    private String text;   // 纯文本内容
}
