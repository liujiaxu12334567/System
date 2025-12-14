package com.project.system.dto;

public class AttendanceRecordResponse {
    private Long classId;
    private Long courseId;
    private Long studentId;
    private String courseName;
    private String studentName;
    private String date;
    private Boolean present;

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public Boolean getPresent() { return present; }
    public void setPresent(Boolean present) { this.present = present; }
}
