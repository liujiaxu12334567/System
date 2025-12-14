package com.project.system.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.system.entity.Application;
import com.project.system.entity.Notification;
import com.project.system.entity.User;
import com.project.system.entity.Course;
import com.project.system.entity.AttendanceRecord;
import com.project.system.entity.AttendanceSummary;
import com.project.system.mapper.ApplicationMapper;
import com.project.system.mapper.NotificationMapper;
import com.project.system.mapper.AttendanceSummaryMapper;
import com.project.system.mapper.AttendanceRecordMapper;
import com.project.system.mapper.UserMapper;
import com.project.system.mapper.CourseMapper;
import com.project.system.service.QualityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QualityServiceImpl implements QualityService {

    @Autowired
    private ApplicationMapper applicationMapper;
    @Autowired
    private NotificationMapper notificationMapper;
    @Autowired
    private AttendanceSummaryMapper attendanceSummaryMapper;
    @Autowired
    private AttendanceRecordMapper attendanceRecordMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CourseMapper courseMapper;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userMapper.findByUsername(username);
    }

    @Override
    public void submitCreditApplication(String type, String title, String desc, MultipartFile file) throws IOException {
        User student = getCurrentUser();

        // 真实文件上传逻辑
        String fileUrl = "";
        if (file != null && !file.isEmpty()) {
            File directory = new File(uploadDir);
            if (!directory.exists()) directory.mkdirs();
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.lastIndexOf(".") > 0 ?
                    originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
            String uniqueName = UUID.randomUUID().toString() + extension;
            Path path = Paths.get(uploadDir + File.separator + uniqueName);
            Files.write(path, file.getBytes());
            // 返回相对路径，前端通过 /uploads 代理访问
            fileUrl = "/uploads/" + uniqueName;
        }

        Map<String, String> contentMap = new HashMap<>();
        contentMap.put("title", title);
        contentMap.put("desc", desc);
        contentMap.put("img", fileUrl);

        Application app = new Application();
        app.setTeacherId(student.getUserId()); // 申请人ID
        app.setTeacherName(student.getRealName());
        app.setType(type);
        app.setContent(new ObjectMapper().writeValueAsString(contentMap));
        app.setStatus("PENDING");
        app.setCreateTime(LocalDateTime.now());
        app.setTargetId(student.getClassId()); // 班级ID

        applicationMapper.insert(app);
    }

    @Override
    public void submitLeaveApplication(Application app) {
        User student = getCurrentUser();
        app.setTeacherId(student.getUserId());
        app.setTeacherName(student.getRealName());
        app.setStatus("PENDING");
        app.setCreateTime(LocalDateTime.now());
        app.setTargetId(student.getClassId());
        applicationMapper.insert(app);
    }

    @Override
    public List<Map<String, Object>> getManagedAttendance() {
        User qualityTeacher = getCurrentUser();
        String classesStr = qualityTeacher.getTeachingClasses();
        if (classesStr == null || classesStr.isEmpty()) {
            return List.of();
        }
        List<Long> classIds = classesStrToList(classesStr).stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
        if (classIds.isEmpty()) return List.of();

        List<AttendanceSummary> summaries = attendanceSummaryMapper.selectByClassIds(classIds);
        Map<Long, long[]> agg = new HashMap<>();
        for (var a : summaries) {
            long expected = a.getExpected() == null ? 0 : a.getExpected();
            long present = a.getPresent() == null ? 0 : a.getPresent();
            long[] pair = agg.computeIfAbsent(a.getClassId(), k -> new long[2]);
            pair[0] += present;
            pair[1] += expected;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        agg.forEach((cls, pair) -> {
            long present = pair[0];
            long expected = pair[1];
            double rate = expected > 0 ? Math.round((present * 1000.0 / expected)) / 10.0 : 0d;
            result.add(Map.of(
                    "classId", cls,
                    "present", present,
                    "expected", expected,
                    "rate", rate
            ));
        });
        return result;
    }

    @Override
    public List<com.project.system.dto.AttendanceRecordResponse> getAttendanceRecords(Long classId, Long courseId, Long studentId) {
        User qualityTeacher = getCurrentUser();
        String classesStr = qualityTeacher.getTeachingClasses();
        if (classesStr == null || classesStr.isEmpty()) {
            throw new RuntimeException("未分配班级，无权查看");
        }
        List<String> managed = classesStrToList(classesStr);
        if (classId == null || managed.stream().noneMatch(c -> c.equals(String.valueOf(classId)))) {
            throw new RuntimeException("无权查看该班级");
        }

        List<AttendanceRecord> list = attendanceRecordMapper.selectByClassCourseStudent(classId, courseId, studentId);
        if (list == null || list.isEmpty()) return List.of();

        Map<Long, User> studentMap = new HashMap<>();
        userMapper.selectStudentsByClassIds(List.of(classId)).forEach(u -> studentMap.put(u.getUserId(), u));

        Set<Long> courseIds = list.stream().map(AttendanceRecord::getCourseId).collect(Collectors.toSet());
        Map<Long, Course> courseMap = courseMapper.selectAllCourses().stream()
                .filter(c -> c.getId() != null && courseIds.contains(c.getId()))
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        List<com.project.system.dto.AttendanceRecordResponse> resp = new ArrayList<>();
        for (AttendanceRecord r : list) {
            com.project.system.dto.AttendanceRecordResponse dto = new com.project.system.dto.AttendanceRecordResponse();
            dto.setClassId(r.getClassId());
            dto.setCourseId(r.getCourseId());
            dto.setStudentId(r.getStudentId());
            dto.setDate(r.getDate() != null ? r.getDate().toString() : null);
            dto.setPresent(r.getPresent());
            dto.setCourseName(courseMap.get(r.getCourseId()) != null ? courseMap.get(r.getCourseId()).getName() : null);
            dto.setStudentName(studentMap.get(r.getStudentId()) != null ? studentMap.get(r.getStudentId()).getRealName() : null);
            resp.add(dto);
        }
        return resp;
    }

    private List<String> classesStrToList(String classesStr) {
        return List.of(classesStr.split(",")).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    @Override
    public List<Application> getManagedApplications() {
        User qualityTeacher = getCurrentUser();
        String classesStr = qualityTeacher.getTeachingClasses();
        if (classesStr == null || classesStr.isEmpty()) {
            return List.of();
        }

        List<String> managedClassIds = classesStrToList(classesStr);
        List<Application> all = applicationMapper.findByStatus("PENDING");

        return all.stream()
                .filter(a -> {
                    boolean isQualityType = a.getType().startsWith("QUALITY_") || "LEAVE_APPLICATION".equals(a.getType());
                    boolean isManagedClass = a.getTargetId() != null && managedClassIds.contains(String.valueOf(a.getTargetId()));
                    return isQualityType && isManagedClass;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void reviewApplication(Long id, String status) {
        Application app = applicationMapper.findById(id);
        if(app != null) {
            User qualityTeacher = getCurrentUser();
            String classesStr = qualityTeacher.getTeachingClasses();
            if (classesStr == null || classesStr.isEmpty() || app.getTargetId() == null ||
                    classesStrToList(classesStr).stream().noneMatch(c -> c.equals(String.valueOf(app.getTargetId())))) {
                throw new RuntimeException("无权审批：该班级不在您的负责范围内");
            }

            app.setStatus(status);
            applicationMapper.updateStatus(id, status);

            // 发送通知给学生
            Notification note = new Notification();
            note.setUserId(app.getTeacherId()); // 学生ID

            // 【修复点 1】设置关联ID，方便跳转（可选）
            note.setRelatedId(id);

            // 【修复点 2】必须设置 type，否则数据库报错 "Column 'type' cannot be null"
            note.setType("APPLICATION_RESULT");

            note.setTitle("申请审批结果通知");
            String resultText = "APPROVED".equals(status) ? "通过" : "被驳回";

            String typeText = "申请";
            if (app.getType() != null) {
                if (app.getType().contains("LEAVE")) typeText = "请假申请";
                else if (app.getType().contains("QUALITY")) typeText = "素质学分申请";
            }

            note.setMessage("您的 " + typeText + " 已" + resultText + "。");
            note.setCreateTime(LocalDateTime.now());
            note.setIsRead(false);
            note.setIsActionRequired(false);
            note.setSenderName(getCurrentUser().getRealName());

            notificationMapper.insert(note);

            if("APPROVED".equals(status) && "QUALITY_COMPETITION".equals(app.getType())) {
                System.out.println("学生 " + app.getTeacherName() + " 获得 0.2 素质学分");
            }
        }
    }
    @Override
    public void sendNotificationToManagedClasses(String title, String content) {
        User qualityTeacher = getCurrentUser();
        String classesStr = qualityTeacher.getTeachingClasses();

        if (classesStr == null || classesStr.isEmpty()) {
            throw new RuntimeException("您尚未分配负责的班级");
        }

        // 解析班级ID列表
        List<Long> classIds = classesStrToList(classesStr).stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // 查找这些班级下的所有学生 (复用 UserMapper 中已有的 selectStudentsByClassIds)
        List<User> students = userMapper.selectStudentsByClassIds(classIds);

        if (students.isEmpty()) {
            throw new RuntimeException("负责的班级下暂无学生");
        }

        // 批量发送通知
        for (User student : students) {
            Notification note = new Notification();
            note.setUserId(student.getUserId());
            note.setType("QUALITY_NOTICE"); // 设置特定的通知类型
            note.setTitle(title);
            note.setMessage(content);
            note.setIsRead(false);
            note.setIsActionRequired(false);
            note.setSenderName(qualityTeacher.getRealName()); // 发送者为素质教师
            note.setCreateTime(LocalDateTime.now());

            notificationMapper.insert(note);
        }
    }
}
