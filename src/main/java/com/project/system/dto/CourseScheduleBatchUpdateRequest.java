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
        /**
         * 清空该课程的排课信息（dayOfWeek/startTime/endTime 置空）。
         * 用于“无课”场景：从课表网格中移除课程时可传 true。
         */
        private Boolean clear;
        private Integer dayOfWeek; // 1=周一...7=周日
        private Integer periodIndex; // 1..5
        private String startTime;
        private String endTime;
    }
}
