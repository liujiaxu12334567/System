package com.project.system.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class AnalysisResult {
    private Long id;
    private Long courseId;
    private String metric;
    private String valueJson;
    private Timestamp generatedAt;
    private String eventId;
}
