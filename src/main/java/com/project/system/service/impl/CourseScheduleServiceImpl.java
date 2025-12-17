package com.project.system.service.impl;

import com.project.system.dto.CourseTimetableResponse;
import com.project.system.entity.Course;
import com.project.system.entity.CourseScheduleSlot;
import com.project.system.mapper.CourseMapper;
import com.project.system.mapper.CourseScheduleMapper;
import com.project.system.service.CourseScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CourseScheduleServiceImpl implements CourseScheduleService {
    @Autowired private CourseMapper courseMapper;
    @Autowired private CourseScheduleMapper courseScheduleMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_ONLY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void enrichCoursesWithSchedule(List<Course> courses) {
        if (courses == null || courses.isEmpty()) return;
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        for (Course course : courses) {
            if (course == null || course.getId() == null) continue;
            Optional<SlotWindow> active = findActiveSlotWindow(course.getId(), today, now);
            if (active.isPresent()) {
                SlotWindow slot = active.get();
                course.setStartTime(formatDateTime(slot.start));
                course.setEndTime(formatDateTime(slot.end));
                course.setStatus("进行中");
                continue;
            }
            Optional<SlotWindow> next = findNextSlotWindow(course.getId(), today, now);
            if (next.isPresent()) {
                SlotWindow slot = next.get();
                course.setStartTime(formatDateTime(slot.start));
                course.setEndTime(formatDateTime(slot.end));
                course.setStatus("未开始");
            } else {
                course.setStartTime(null);
                course.setEndTime(null);
                course.setStatus("未开始");
            }
        }
    }

    @Override
    public CourseTimetableResponse getClassTimetable(Long classId) {
        if (classId == null) throw new RuntimeException("classId不能为空");
        CourseTimetableResponse resp = new CourseTimetableResponse();
        List<Course> courses = courseMapper.selectCoursesByClassIdOrderByStartTime(classId);
        if (courses == null) {
            courses = new ArrayList<>();
        }
        resp.setCourses(courses);
        resp.setSchedules(courseScheduleMapper.selectByClassId(classId));
        enrichCoursesWithSchedule(courses);
        return resp;
    }

    private Optional<SlotWindow> findActiveSlotWindow(Long courseId, LocalDate today, LocalTime now) {
        int dayOfWeek = today.getDayOfWeek().getValue();
        List<CourseScheduleSlot> slots = courseScheduleMapper.selectByCourseIdAndDay(courseId, dayOfWeek);
        if (slots == null || slots.isEmpty()) return Optional.empty();
        for (CourseScheduleSlot slot : slots) {
            LocalTime start = parseSlotTime(slot.getStartTime());
            if (start == null) continue;
            LocalTime end = parseSlotTime(slot.getEndTime());
            if (end == null) {
                end = start.plusMinutes(90);
            }
            if (!now.isBefore(start) && !now.isAfter(end)) {
                LocalDateTime startDt = LocalDateTime.of(today, start);
                LocalDateTime endDt = LocalDateTime.of(today, end);
                return Optional.of(new SlotWindow(startDt, endDt));
            }
        }
        return Optional.empty();
    }

    private Optional<SlotWindow> findNextSlotWindow(Long courseId, LocalDate today, LocalTime now) {
        int todayDow = today.getDayOfWeek().getValue();
        SlotWindow best = null;
        for (int delta = 0; delta < 7; delta++) {
            int targetDow = ((todayDow - 1 + delta) % 7) + 1;
            List<CourseScheduleSlot> slots = courseScheduleMapper.selectByCourseIdAndDay(courseId, targetDow);
            if (slots == null || slots.isEmpty()) continue;
            for (CourseScheduleSlot slot : slots) {
                LocalTime start = parseSlotTime(slot.getStartTime());
                if (start == null) continue;
                int offset = delta;
                if (delta == 0 && !start.isAfter(now)) {
                    offset = 7;
                }
                LocalDate date = today.plusDays(offset);
                LocalDateTime startDt = LocalDateTime.of(date, start);
                LocalTime end = parseSlotTime(slot.getEndTime());
                LocalDateTime endDt = end != null ? LocalDateTime.of(date, end) : startDt;
                if (best == null || startDt.isBefore(best.start)) {
                    best = new SlotWindow(startDt, endDt);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private LocalTime parseSlotTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalTime.parse(value, TIME_ONLY_FORMATTER);
        } catch (Exception e) {
            try {
                return LocalTime.parse(value);
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(FORMATTER);
    }

    private static class SlotWindow {
        final LocalDateTime start;
        final LocalDateTime end;

        SlotWindow(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }
}
