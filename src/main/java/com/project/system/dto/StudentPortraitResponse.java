package com.project.system.dto;

import lombok.Data;

@Data
public class StudentPortraitResponse {
    private Long userId;

    private double attendanceRate;
    private long attendancePresent;
    private long attendanceTotal;

    private double submissionRate;
    private long submittedCount;
    private long taskTotal;

    private double interactionScore;
    private long interactionCount;

    private double portraitScore;
}

