package com.project.system.service;

import com.project.system.dto.CourseTimetableResponse;
import com.project.system.entity.Course;

import java.util.List;

public interface CourseScheduleService {
    /**
     * 为指定课程列表填充排课相关的时间/状态信息。
     */
    void enrichCoursesWithSchedule(List<Course> courses);

    /**
     * 获取某个班级的课程表（课程 + slot 列表）。
     */
    CourseTimetableResponse getClassTimetable(Long classId);
}
