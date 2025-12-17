package com.project.system.dto;

import com.project.system.entity.Course;
import com.project.system.entity.CourseScheduleSlot;
import lombok.Data;

import java.util.List;

@Data
public class CourseTimetableResponse {
    private List<Course> courses;
    private List<CourseScheduleSlot> schedules;
}

