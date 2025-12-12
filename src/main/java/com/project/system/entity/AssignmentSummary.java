package com.project.system.entity;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class AssignmentSummary {
    private Long id;
    private Long courseId;
    private Long classId;
    private Date date;
    private Integer total;
    private Integer submitted;
    private Timestamp createdAt;
}
