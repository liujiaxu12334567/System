package com.project.system.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Notification {
    private Long id;
    private Long userId;        // 接收人ID
    private Long relatedId;     // 关联ID
    private String type;        // 类型
    private String title;       // 标题
    private String message;     // 内容
    private Boolean isRead;     // 是否已读

    // 【新增字段】
    private Boolean isActionRequired; // 是否需要用户填写信息返回
    private String userReply;         // 用户填写的回复信息
    private String batchId;           // 批次ID (用于管理员统计同一次群发的数据)
    private String senderName;        // 发送者姓名

    private LocalDateTime createTime;
}