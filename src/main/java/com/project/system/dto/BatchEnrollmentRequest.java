package com.project.system.dto;

import lombok.Data;
import java.util.List;

// 用于批量创建学生账号和分配班级的请求体
@Data
public class BatchEnrollmentRequest {
    // 方式一：学号范围批量生成 (例如: 24107311201)
    private String startUsername;
    private String endUsername;

    // 方式二：导入表格/列表批量添加 (可选，但后端逻辑已预留)
    private List<StudentInfo> studentList;

    // 目标班级ID (必须指定)
    private Long targetClassId;

    // 【新增字段】所属专业
    private String major;

    // 嵌套类，用于接收导入的学生信息
    @Data
    public static class StudentInfo {
        private String username;
        private String realName;
    }
}