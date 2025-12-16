package com.project.system.dto;

import lombok.Data;

@Data
public class CourseAssignRequest {
    private Long groupId;
    // 【新增】按专业下发：只允许选择该专业的班级
    private String major;
    private Long classId;
    private Long teacherId;
}
