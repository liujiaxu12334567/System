package com.project.system.entity;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class AttendanceRecord {
    private Long id;
    private Long classId;
    private Long courseId;
    private Long studentId;
    private Date date;
    private Boolean present;
    private String batchId;
    private Timestamp createdAt;

    // 显式 getter，确保方法引用可用（避免 Lombok 环境缺失导致编译失败）
    public Long getId() {
        return id;
    }
    public Long getClassId() {
        return classId;
    }
    public Long getCourseId() {
        return courseId;
    }
    public Long getStudentId() {
        return studentId;
    }
    public Date getDate() {
        return date;
    }
    public Boolean getPresent() {
        return present;
    }
    public String getBatchId() {
        return batchId;
    }
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public void setClassId(Long classId) {
        this.classId = classId;
    }
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }
    public void setDate(Date date) {
        this.date = date;
    }
    public void setPresent(Boolean present) {
        this.present = present;
    }
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
