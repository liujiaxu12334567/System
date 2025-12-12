package com.project.system.entity;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class TeacherInteraction {
    private Long id;
    private Long courseId;
    private Long classId;
    private Long teacherId;
    private Date date;
    private Integer talkTimeSeconds;
    private Integer studentTalkSeconds;
    private Integer groupTalkSeconds;
    private Integer interactionCount;
    private Timestamp createdAt;
}
