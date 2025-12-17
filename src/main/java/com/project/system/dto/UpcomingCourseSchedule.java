package com.project.system.dto;

import lombok.Data;

@Data
public class UpcomingCourseSchedule {
    private Long scheduleId;
    private Long classId;
    private Integer dayOfWeek;
    private Integer periodIndex;
    private String startTime;
    private String endTime;
    private Long courseId;
    private String courseName;
    private Long teacherId;
}

