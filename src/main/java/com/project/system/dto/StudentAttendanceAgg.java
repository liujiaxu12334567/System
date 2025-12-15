package com.project.system.dto;

import lombok.Data;

@Data
public class StudentAttendanceAgg {
    private Long userId;
    private Long presentCount;
    private Long totalCount;
}

