package com.project.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseGroup {
    private Long groupId;
    private String name;
    private String semester;
    private Long leaderId;
    private String leaderName;
    private LocalDateTime createTime;
}

