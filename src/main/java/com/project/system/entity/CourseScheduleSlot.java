package com.project.system.entity;

import lombok.Data;

/**
 * 班级课表：按「周几 × 第几节」的排课明细。
 * courseId 允许为空，表示该格子无课（但仍可用于持久化节次时间）。
 */
@Data
public class CourseScheduleSlot {
    private Long id;
    private Long classId;
    private Integer dayOfWeek;   // 1=周一...7=周日
    private Integer periodIndex; // 1..5
    private String startTime;    // HH:mm:ss
    private String endTime;      // HH:mm:ss，可空
    private Long courseId;       // 可空
}

