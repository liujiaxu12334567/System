package com.project.system.dto;

import lombok.Data;

@Data
public class CourseGroupCreateRequest {
    private String name;
    private String semester;
    private Long leaderId; // 可为空：先创建课程组，再指定组长
}

