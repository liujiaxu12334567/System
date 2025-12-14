package com.project.system.dto;

import lombok.Data;

@Data
public class CourseAssignRequest {
    private Long groupId;
    private Long classId;
    private Long teacherId;
}

