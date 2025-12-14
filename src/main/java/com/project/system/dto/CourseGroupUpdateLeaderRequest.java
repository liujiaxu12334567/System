package com.project.system.dto;

import lombok.Data;

@Data
public class CourseGroupUpdateLeaderRequest {
    private Long groupId;
    private Long leaderId; // 可为空：取消组长
}

