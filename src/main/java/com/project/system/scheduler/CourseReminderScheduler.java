package com.project.system.scheduler;

import com.project.system.dto.UpcomingCourseSchedule;
import com.project.system.entity.Notification;
import com.project.system.entity.User;
import com.project.system.mapper.CourseScheduleMapper;
import com.project.system.mapper.NotificationMapper;
import com.project.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Component
public class CourseReminderScheduler {

    private static final int REMINDER_MINUTES = 15;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Autowired private CourseScheduleMapper courseScheduleMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private NotificationMapper notificationMapper;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    /**
     * 每分钟检查一次：对“15分钟后即将开始”的课程发送提醒通知（老师+学生）。
     * 通过 Redis key 去重，避免重复发送。
     */
    @Scheduled(cron = "0 * * * * *")
    public void sendUpcomingCourseReminders() {
        LocalDateTime now = LocalDateTime.now();
        int dow = now.getDayOfWeek().getValue(); // 1=周一...7=周日

        // 目标窗口：当前 +15 分钟，给 90 秒的容错区间
        LocalTime from = now.plusMinutes(REMINDER_MINUTES).minusSeconds(30).toLocalTime();
        LocalTime to = now.plusMinutes(REMINDER_MINUTES).plusSeconds(60).toLocalTime();

        // 跨天窗口直接跳过（避免 23:59 附近计算出错误区间）
        if (to.isBefore(from)) return;

        List<UpcomingCourseSchedule> schedules = courseScheduleMapper.selectUpcomingStartingBetween(
                dow, from.format(TIME_FMT), to.format(TIME_FMT));
        if (schedules == null || schedules.isEmpty()) return;

        LocalDate today = now.toLocalDate();
        for (UpcomingCourseSchedule sch : schedules) {
            if (sch == null) continue;
            if (sch.getClassId() == null || sch.getCourseId() == null) continue;

            String key = "reminder:slot:" + sch.getClassId() + ":" + sch.getDayOfWeek() + ":" + sch.getPeriodIndex() + ":" + sch.getCourseId() + ":" + today;
            Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(26));
            if (!Boolean.TRUE.equals(first)) continue;

            String startTime = sch.getStartTime() == null ? "" : String.valueOf(sch.getStartTime());
            String title = "快到上课时间了";
            String message = "课程《" + safe(sch.getCourseName()) + "》将于 " + startTime + " 开始（第" + safeInt(sch.getPeriodIndex()) + "节，约" + REMINDER_MINUTES + "分钟后）";

            // 老师提醒
            if (sch.getTeacherId() != null) {
                insertNotification(sch.getTeacherId(), sch.getCourseId(), title, message);
            }

            // 学生提醒（按班级）
            List<User> students = userMapper.selectStudentsByClassIds(Collections.singletonList(sch.getClassId()));
            if (students == null || students.isEmpty()) continue;
            for (User s : students) {
                if (s == null || s.getUserId() == null) continue;
                insertNotification(s.getUserId(), sch.getCourseId(), title, message);
            }
        }
    }

    private void insertNotification(Long userId, Long courseId, String title, String message) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setRelatedId(courseId);
        n.setType("CLASS_REMINDER");
        n.setTitle(title);
        n.setMessage(message);
        n.setIsActionRequired(false);
        n.setSenderName("系统");
        notificationMapper.insert(n);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String safeInt(Integer v) {
        return v == null ? "" : String.valueOf(v);
    }
}
