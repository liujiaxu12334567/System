package com.project.system.dto;

import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class TeacherAnalysisResponse {
    private Long teacherId;
    private String teacherName;
    private Integer courseCount;
    private CourseAnalysisItem latest;
    private List<CourseAnalysisItem> courses;

    @Data
    public static class CourseAnalysisItem {
        private Long courseId;
        private String courseName;
        private String semester;
        private Long classId;
        private String metric;
        private String valueJson;
        private Timestamp generatedAt;
    }
}

