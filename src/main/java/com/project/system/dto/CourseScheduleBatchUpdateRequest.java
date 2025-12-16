package com.project.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class CourseScheduleBatchUpdateRequest {
    private Long classId;
    private List<Item> schedules;

    @Data
    public static class Item {
        private Long courseId;
        private Integer dayOfWeek; // 1=周一...7=周日
        private String startTime;
        private String endTime;
    }
}
